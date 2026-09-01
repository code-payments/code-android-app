import Foundation
import Testing
@testable import SharedCoreKit

@Suite struct Base58Tests {

    struct Fixture: Decodable {
        struct Vector: Decodable {
            let name: String
            let bytes: String
            let base58: String
        }

        let vectors: [Vector]
    }

    @Test func matchesTheCrossPlatformVectors() throws {
        let fixture = try Fixtures.load("base58", as: Fixture.self)
        #expect(fixture.vectors.count == 7)

        for vector in fixture.vectors {
            let bytes = try #require(Data(hex: vector.bytes), "\(vector.name): bad fixture hex")
            #expect(Base58.encode(bytes) == vector.base58, "\(vector.name): encode")
            #expect(Base58.decode(vector.base58) == bytes, "\(vector.name): decode")
        }
    }

    @Test func rejectsCharactersOutsideTheAlphabet() {
        // 0, I, O and l are the four excluded from the Bitcoin alphabet.
        #expect(Base58.decode("0OIl") == nil)
    }
}
