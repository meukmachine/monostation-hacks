package flasher

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.MidiPortConnectionState
import dev.atsushieno.ktmidi.MidiPortDetails
import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.withTimeoutOrNull
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * ktmidi's MidiAccess over the browser's Web MIDI API. ktmidi 0.12 has a Web MIDI backend
 * only for Kotlin/Wasm (ktmidi-wasm-js); its Kotlin/JS one goes through the jzz npm package.
 * The MIDIAccess maps are live, so ports that appear later (the bootloader) show up here.
 */
class WebMidiAccess private constructor(private var access: dynamic) : MidiAccess(), Rescannable {
    companion object {
        val isSupported: Boolean get() = window.navigator.asDynamic().requestMIDIAccess != undefined

        /** The last access handed out; a new one stops its port log, or each Flash adds a logger. */
        private var current: WebMidiAccess? = null

        /** Asks for MIDI with SysEx (the browser shows a permission prompt). */
        suspend fun request(): WebMidiAccess {
            current?.detach()
            return WebMidiAccess(requestAccess()).also { current = it }
        }

        private suspend fun requestAccess(): dynamic {
            val options: dynamic = js("({})")
            options.sysex = true
            return window.navigator.asDynamic().requestMIDIAccess(options).unsafeCast<Promise<Any>>().await()
        }
    }

    private var portChange: ((String) -> Unit)? = null
    /** Last state per port, kept across rescans so only real changes are reported. */
    private val seen = mutableMapOf<String, String>()
    private var lastRescan: TimeMark? = null

    /**
     * Firefox (seen on Linux) never updates the port maps of a MIDIAccess, nor fires statechange,
     * when the unit reboots into the bootloader; a new requestMIDIAccess lists the ports again.
     * At most once a second: each request makes Firefox open new ALSA client ports.
     */
    override suspend fun rescan() {
        if (lastRescan?.let { it.elapsedNow().inWholeMilliseconds < 1000 } == true) return
        lastRescan = TimeSource.Monotonic.markNow()
        access.onstatechange = null
        access = requestAccess()
        portChange?.let { attach(it) }
    }

    override val name = "Web MIDI"

    /** Stops reporting port changes (the browser keeps firing statechange on old accesses). */
    private fun detach() {
        portChange = null
        access.onstatechange = null
    }

    /** Called for each port connect/disconnect the browser reports (for the log). */
    fun onPortChange(callback: (String) -> Unit) {
        portChange = callback
        attach(callback)
    }

    private val names = mutableMapOf<String, String>()
    private fun key(port: dynamic): String {
        val k = "${port.type} ${port.id}"
        names[k] = "${port.type} '${port.name}'"
        return k
    }

    private fun attach(callback: (String) -> Unit) {
        // Opening a port also fires statechange (its connection changes); report only state changes,
        // and skip the ALSA ports Firefox itself creates ("WebMIDI input:Input connection 128:0").
        val first = seen.isEmpty()
        fun update(p: dynamic) {
            val state = p.state as String
            val before = seen.put(key(p), state)
            if (before != state && !(first && before == null) && !(p.name as String).startsWith("WebMIDI "))
                callback("${p.type} '${p.name}' $state")
        }
        // A rescan may find ports without any statechange event: compare with what we knew.
        val now = mutableSetOf<String>()
        for (map in listOf(access.inputs, access.outputs))
            map.forEach { port: dynamic, _: dynamic -> now += key(port); update(port) }
        for ((k, state) in seen.entries.toList())
            if (k !in now && state != "disconnected") {
                seen[k] = "disconnected"
                if (!first && names[k]?.contains("'WebMIDI ") != true) callback("${names[k]} disconnected")
            }
        access.onstatechange = { ev: dynamic -> update(ev.port) }
    }

    override val inputs: Iterable<MidiPortDetails> get() = ports(access.inputs)
    override val outputs: Iterable<MidiPortDetails> get() = ports(access.outputs)

    private fun ports(map: dynamic): List<MidiPortDetails> {
        val list = mutableListOf<MidiPortDetails>()
        map.forEach { port: dynamic, _: dynamic -> if (port.state != "disconnected") list += WebMidiPortDetails(port) }
        return list
    }

    override suspend fun openInput(portId: String): MidiInput {
        val port = access.inputs.get(portId) ?: throw FlashError("MIDI input $portId is gone")
        openPort(port)
        return WebMidiInput(WebMidiPortDetails(port))
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val port = access.outputs.get(portId) ?: throw FlashError("MIDI output $portId is gone")
        openPort(port)
        return WebMidiOutput(WebMidiPortDetails(port))
    }
}

/**
 * Firefox keeps listing a port after the unit is switched off, and opening it then never
 * settles; give up after a few seconds.
 */
private suspend fun openPort(port: dynamic) {
    withTimeoutOrNull(OPEN_TIMEOUT_MS) { (port.open() as Promise<Any>).await() }
        ?: throw FlashError("'${port.name}' doesn't open; is the unit switched on and connected?")
}

private const val OPEN_TIMEOUT_MS = 3_000L

private class WebMidiPortDetails(val port: dynamic) : MidiPortDetails {
    override val id: String get() = port.id as String
    override val manufacturer: String? get() = port.manufacturer as String?
    override val name: String? get() = port.name as String?
    override val version: String? get() = port.version as String?
    override val midiTransportProtocol = 1
}

private class WebMidiInput(override val details: WebMidiPortDetails) : MidiInput {
    private var listener: OnMidiReceivedEventListener? = null
    private val handler: (dynamic) -> Unit = { ev ->
        val data = ev.data.unsafeCast<Uint8Array>()
        // Same bytes, viewed as signed: a copy-free ByteArray.
        val bytes = Int8Array(data.buffer, data.byteOffset, data.length).unsafeCast<ByteArray>()
        listener?.onEventReceived(bytes, 0, bytes.size, 0)
    }
    override var connectionState = MidiPortConnectionState.OPEN

    init { details.port.addEventListener("midimessage", handler) }

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) { this.listener = listener }

    override fun close() {
        // Only detach: other Device objects may still use the same browser port.
        details.port.removeEventListener("midimessage", handler)
        listener = null
        connectionState = MidiPortConnectionState.CLOSED
    }
}

private class WebMidiOutput(override val details: WebMidiPortDetails) : MidiOutput {
    override var connectionState = MidiPortConnectionState.OPEN

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        val view = mevent.unsafeCast<Int8Array>()
        details.port.send(Uint8Array(view.buffer, view.byteOffset + offset, length))
    }

    override fun close() { connectionState = MidiPortConnectionState.CLOSED }
}
