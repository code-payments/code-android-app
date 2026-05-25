package com.getcode.utils.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionTypeTest {

    @Test
    fun wifiIsWifi() = assertTrue(ConnectionType.Wifi.isWifi())

    @Test
    fun cellularIsNotWifi() = assertFalse(ConnectionType.Cellular.isWifi())

    @Test
    fun unknownIsNotWifi() = assertFalse(ConnectionType.Unknown.isWifi())

    @Test
    fun cellularIsCellular() = assertTrue(ConnectionType.Cellular.isCellular())

    @Test
    fun wifiIsNotCellular() = assertFalse(ConnectionType.Wifi.isCellular())

    @Test
    fun unknownIsNotCellular() = assertFalse(ConnectionType.Unknown.isCellular())

    @Test
    fun wifiIsValid() = assertTrue(ConnectionType.Wifi.isValid())

    @Test
    fun cellularIsValid() = assertTrue(ConnectionType.Cellular.isValid())

    @Test
    fun unknownIsNotValid() = assertFalse(ConnectionType.Unknown.isValid())
}

class SignalStrengthTest {

    @Test
    fun weakIsWeakOrPoor() = assertTrue(SignalStrength.Weak.isWeakOrPoor())

    @Test
    fun poorIsWeakOrPoor() = assertTrue(SignalStrength.Poor.isWeakOrPoor())

    @Test
    fun goodIsNotWeakOrPoor() = assertFalse(SignalStrength.Good.isWeakOrPoor())

    @Test
    fun greatIsNotWeakOrPoor() = assertFalse(SignalStrength.Great.isWeakOrPoor())

    @Test
    fun strongIsNotWeakOrPoor() = assertFalse(SignalStrength.Strong.isWeakOrPoor())

    @Test
    fun unknownIsNotWeakOrPoor() = assertFalse(SignalStrength.Unknown.isWeakOrPoor())

    @Test
    fun unknownIsNotKnown() = assertFalse(SignalStrength.Unknown.isKnown())

    @Test
    fun weakIsKnown() = assertTrue(SignalStrength.Weak.isKnown())

    @Test
    fun goodIsKnown() = assertTrue(SignalStrength.Good.isKnown())

    @Test
    fun strongIsKnown() = assertTrue(SignalStrength.Strong.isKnown())
}
