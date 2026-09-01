import Foundation
import Testing
@testable import SharedCoreKit

/// Anchored to published vectors rather than to whatever the Kotlin currently returns, so the test
/// says "correct" and not merely "unchanged".
@Suite struct HashesTests {

    @Test func sha256MatchesTheKnownDigests() throws {
        #expect(
            SharedHash.sha256(Data()).hexString
                == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        #expect(
            SharedHash.sha256(Data("abc".utf8)).hexString
                == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        )
    }

    @Test func sha256TwiceIsSha256OfSha256() {
        let input = Data("abc".utf8)
        #expect(SharedHash.sha256Twice(input) == SharedHash.sha256(SharedHash.sha256(input)))
    }

    @Test func sha512MatchesTheKnownDigests() {
        #expect(
            SharedHash.sha512(Data()).hexString
                == "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
                + "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
        )
        #expect(
            SharedHash.sha512(Data("abc".utf8)).hexString
                == "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
        )
    }

    /// RFC 4231 test case 2: key "Jefe", data "what do ya want for nothing?".
    @Test func hmacMatchesRfc4231Case2() {
        let key = Data("Jefe".utf8)
        let message = Data("what do ya want for nothing?".utf8)

        #expect(
            SharedHash.hmacSHA256(key: key, message: message).hexString
                == "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        )
        #expect(
            SharedHash.hmacSHA512(key: key, message: message).hexString
                == "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554"
                + "9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737"
        )
    }

    /// RFC 6070 is SHA-1 only, so PBKDF2-HMAC-SHA512 anchors on the BIP-39 parameters this is
    /// actually used with: 2048 iterations, salt "mnemonic" + passphrase, 64-byte output. The
    /// expected seed is the all-"abandon" test mnemonic from the BIP-39 spec vectors.
    @Test func pbkdf2MatchesTheBip39Vector() {
        let mnemonic = Array(repeating: "abandon", count: 11).joined(separator: " ") + " about"

        let seed = SharedHash.pbkdf2SHA512(
            password: mnemonic,
            salt: "mnemonic",
            iterations: 2048,
            keyLength: 64
        )

        #expect(
            seed.hexString
                == "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1"
                + "9a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4"
        )
    }
}

extension Data {

    var hexString: String { map { String(format: "%02x", $0) }.joined() }
}
