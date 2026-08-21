import Foundation
import Testing
@testable import SharedCoreKit

@Suite struct SharedCoreKitTests {

    @Test func reachesTheKotlinFramework() {
        #expect(!SharedCoreInfo.version.isEmpty)
    }

    /// Kotlin bytes are signed, so a naive copy mangles anything above 0x7F.
    @Test func highBitBytesSurviveTheByteArrayCopy() {
        let payload = Data([0x00, 0x7F, 0x80, 0xFF] + Array(repeating: UInt8(0xAB), count: 31))

        let array = payload.kotlinByteArray

        #expect(array.size == Int32(payload.count))
        for (offset, byte) in payload.enumerated() {
            #expect(UInt8(bitPattern: array.get(index: Int32(offset))) == byte)
        }
    }

    @Test func rendersAnSvgDocument() {
        let svg = KikCode.svg(payload: Data(repeating: 0xAB, count: 35), dimension: 512, background: "#000000")

        #expect(svg.hasPrefix("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\""))
        #expect(svg.contains("<circle"))
        #expect(svg.contains("#000000"))
        #expect(svg.hasSuffix("</svg>\n"))
    }
}
