package com.rasha.omni.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    // exactly what the server returned on the real machine
    private val real = """{"uptime_s": 67806, "load": [9.0, 10.6, 9.22], "cpus": 4, "mem_total": 8229670912, "mem_available": 2016063488, "temp_c": 94.0, "disks": {"state": {"total": 982820896768, "free": 887847972864}, "media": {"total": 982820896768, "free": 887847972864}}, "battery": {"percent": 71, "status": "Full"}, "ac": true}"""
    private val health = """{"service": "omniptvd", "ok": true, "uptime_s": 61861, "channels": 37, "enabled": 14, "playing": 14, "last_probe": 1792920654.6605957, "probing": false, "epg": false}"""

    @Test fun parsesRealStatus() {
        val s = Parse.status(real)
        assertEquals(4, s.cpus); assertEquals(9.0, s.load1, 0.0); assertEquals(94.0, s.tempC!!, 0.0); assertEquals(71, s.batteryPercent); assertTrue(s.ac)
        assertEquals(2, s.disks.size); assertEquals(0.0966, s.disks["media"]!!.usedFraction, 0.001); assertEquals(0.755, s.memUsedFraction, 0.001)
    }

    @Test fun parsesMissingSensors() {
        val s = Parse.status("""{"uptime_s":5,"load":[0.1,0.1,0.1],"cpus":2,"mem_total":100,"mem_available":50,"temp_c":null,"disks":{},"battery":null,"ac":false}""")
        assertNull(s.tempC); assertNull(s.batteryPercent); assertEquals(0, s.disks.size)
    }

    @Test fun parsesHealth() {
        val h = Parse.health(health)
        assertEquals(14, h.playing); assertEquals(37, h.channels); assertEquals(false, h.epg); assertTrue(h.lastProbe!! > 1.7e9)
    }

    @Test fun alertsOnRealNumbers() {
        val a = Alerts.from(Parse.status(real), Parse.health(health), nowS = 1792920654.0 + 600)
        assertEquals(2, a.size)                       // hot (94 C) and overloaded (load 9 on 4 cores)
    }

    @Test fun overloadedAndHotBothReported() {
        val a = Alerts.from(Parse.status(real), null, 0.0).map { it.text }
        assertTrue(a.any { it.contains("hot") }); assertTrue(a.any { it.contains("overloaded") })
    }

    @Test fun batteryAndDiskAndStaleProbe() {
        val s = Parse.status("""{"uptime_s":5,"load":[0.1],"cpus":4,"mem_total":1000,"mem_available":900,"temp_c":40,"disks":{"media":{"total":1000,"free":10}},"battery":{"percent":12,"status":"Discharging"},"ac":false}""")
        val a = Alerts.from(s, Parse.health(health), nowS = 1792920654.0 + 4 * 3600).map { it.text }
        assertTrue(a.any { it.contains("nearly full") }); assertTrue(a.any { it.contains("on battery") && it.contains("12%") }); assertTrue(a.any { it.contains("last checked 4 hours") })
    }

    @Test fun healthySystemHasNoAlerts() {
        val s = Parse.status("""{"uptime_s":5,"load":[0.2],"cpus":4,"mem_total":1000,"mem_available":600,"temp_c":55,"disks":{"media":{"total":1000,"free":600}},"battery":{"percent":100,"status":"Full"},"ac":true}""")
        assertEquals(0, Alerts.from(s, Parse.health(health), 1792920654.0 + 60).size)
    }

    @Test fun parsesNetwork() {
        val s = Parse.status("""{"uptime_s":5,"load":[0.1],"cpus":4,"mem_total":100,"mem_available":50,"temp_c":40,"disks":{},"battery":null,"ac":true,"net":{"iface":"enp1s0f0","rx_bps":2500000,"tx_bps":125000,"link_mbps":1000}}""")
        assertEquals("enp1s0f0", s.net!!.iface); assertEquals(2_625_000L, s.net!!.total); assertEquals(0.021, s.net!!.fraction!!, 0.0005)
        val first = Parse.status("""{"uptime_s":5,"load":[0.1],"cpus":4,"mem_total":100,"mem_available":50,"temp_c":40,"disks":{},"battery":null,"ac":true,"net":{"iface":"wlp2s0","rx_bps":null,"tx_bps":null,"link_mbps":null}}""")
        assertNull(first.net!!.rxBps); assertNull(first.net!!.fraction)        // the first reading has no rate: shown as "measuring", not as 0
        assertNull(Parse.status("""{"uptime_s":5,"load":[0.1],"cpus":1,"mem_total":1,"mem_available":1,"temp_c":null,"disks":{},"battery":null,"ac":false,"net":null}""").net)
    }

    @Test fun formats() {
        assertEquals("2.5 MB/s", Fmt.rate(2_500_000)); assertEquals("125 kB/s", Fmt.rate(125_000)); assertEquals("40 B/s", Fmt.rate(40))
        assertEquals("827 GB", Fmt.bytes(887847972864)); assertEquals("1.0 TB", Fmt.bytes(1L shl 40)); assertEquals("18 h 50 min", Fmt.duration(67806)); assertEquals("1 d 2 h", Fmt.duration(93600)); assertEquals("5 min", Fmt.duration(300))
    }
}
