import Foundation
import SharedCore
import Testing
@testable import SharedCoreKit

@Suite struct DeviceLinkTests {

    struct Fixture: Decodable {
        struct Case: Decodable {
            let name: String
            let invite: String
            let desktopPublicKey: String
            let desktopSecretKey: String
            let phonePublicKey: String
            let phoneSecretKey: String
            let secret: String
            let code: String
            let entropy: String
            let sealedSeed: String
        }

        struct Rejects: Decodable {
            let invite: [String]
        }

        let cases: [Case]
        let rejects: Rejects
    }

    @Test func derivesTheVectorCodeAndOpensTheVectorSeed() throws {
        let fixture = try Fixtures.load("device_link", as: Fixture.self)
        #expect(!fixture.cases.isEmpty)

        for c in fixture.cases {
            func hex(_ s: String) throws -> Data { try #require(Data(hex: s), "\(c.name): bad fixture hex") }

            let code = DeviceLinkVectors.shared.phoneCode(
                phonePublicKey: try hex(c.phonePublicKey).kotlinByteArray,
                phoneSecretKey: try hex(c.phoneSecretKey).kotlinByteArray,
                desktopPublicKey: try hex(c.desktopPublicKey).kotlinByteArray,
                secret: try hex(c.secret).kotlinByteArray
            )
            #expect(code == c.code, "\(c.name): code")

            let opened = DeviceLinkVectors.shared.desktopOpenSeed(
                desktopPublicKey: try hex(c.desktopPublicKey).kotlinByteArray,
                desktopSecretKey: try hex(c.desktopSecretKey).kotlinByteArray,
                phonePublicKey: try hex(c.phonePublicKey).kotlinByteArray,
                secret: try hex(c.secret).kotlinByteArray,
                sealedSeed: try hex(c.sealedSeed).kotlinByteArray
            )
            #expect(opened.map { Data($0) } == (try hex(c.entropy)), "\(c.name): seed")
        }
    }

    @MainActor
    @Test func acceptsVectorInvitesAndRejectsTheRest() throws {
        let fixture = try Fixtures.load("device_link", as: Fixture.self)
        for c in fixture.cases {
            // A fresh link per invite: scanning moves it to connecting, which ignores later scans.
            let link = SharedDeviceLink { _ in }
            #expect(link.scanned(c.invite), "\(c.name)")
            link.close()
        }
        let link = SharedDeviceLink { _ in }
        defer { link.close() }
        for invite in fixture.rejects.invite {
            #expect(!link.scanned(invite), "\(invite)")
        }
    }
}
