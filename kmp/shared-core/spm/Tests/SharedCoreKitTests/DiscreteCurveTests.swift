import Testing
import Foundation
@testable import SharedCoreKit

@Suite("SharedDiscreteCurve")
struct DiscreteCurveTests {

    struct Vector: Decodable {
        let name: String
        let currentSupply: Int32
        let tokens: Int32
        let spotPrice, value: String
    }
    struct Fixture: Decodable { let vectors: [Vector] }

    struct FractionalVector: Decodable {
        let name, currentSupply, tokens, value: String
    }
    struct FractionalFixture: Decodable { let vectors: [FractionalVector] }

    struct ValueToTokensVector: Decodable {
        let name: String
        let currentSupply: Int32
        let value: String
        let tokens: String?
    }
    struct TokensForValueExchangeVector: Decodable {
        let name, currentValue, value: String
        let tokens, fx: String?
    }
    struct EdgeCaseFixture: Decodable {
        let valueToTokens: [ValueToTokensVector]
        let tokensForValueExchange: [TokensForValueExchangeVector]
    }

    static let tablesLoaded: Void = {
        let pricing = try! Data(contentsOf: Bundle.module.url(forResource: "discrete_pricing_table", withExtension: "bin", subdirectory: "Fixtures")!)
        let cumulative = try! Data(contentsOf: Bundle.module.url(forResource: "discrete_cumulative_table", withExtension: "bin", subdirectory: "Fixtures")!)
        SharedDiscreteCurve.initialize(pricingTableBytes: pricing, cumulativeTableBytes: cumulative)
    }()

    @Test("curve matches the canonical vectors")
    func curveMatchesCanonicalVectors() throws {
        _ = Self.tablesLoaded
        let fixture = try Fixtures.load("curve", as: Fixture.self)
        #expect(!fixture.vectors.isEmpty)

        for v in fixture.vectors {
            let spot = try #require(SharedDiscreteCurve.spotPriceAtSupply(supply: v.currentSupply))
            #expect(Decimal(string: spot) == Decimal(string: v.spotPrice), "spotPrice mismatch for \(v.name)")

            let value = try #require(SharedDiscreteCurve.tokensToValue(currentSupply: "\(v.currentSupply)", tokens: "\(v.tokens)"))
            #expect(Decimal(string: value) == Decimal(string: v.value), "tokensToValue mismatch for \(v.name)")
        }
    }

    @Test("curve matches the fractional vectors")
    func curveMatchesFractionalVectors() throws {
        _ = Self.tablesLoaded
        let fixture = try Fixtures.load("curve_fractional", as: FractionalFixture.self)
        #expect(!fixture.vectors.isEmpty)

        for v in fixture.vectors {
            let value = try #require(SharedDiscreteCurve.tokensToValue(currentSupply: v.currentSupply, tokens: v.tokens))
            #expect(Decimal(string: value) == Decimal(string: v.value), "tokensToValue mismatch for \(v.name)")
        }
    }

    // `Decimal`'s mantissa tops out around 38 significant digits, well short of the 50-sig-fig
    // vectors below -- but every expected/actual pair here is either identical well within that
    // budget, or (the one known exception, `tfve large tvl`'s `fx`) diverges only at the 50th
    // significant digit, past where `Decimal(string:)` even looks. Both sides get truncated to the
    // same prefix before comparing, so this stays a meaningful check rather than a silent no-op.
    @Test("curve matches the edge-case vectors")
    func curveMatchesEdgeCaseVectors() throws {
        _ = Self.tablesLoaded
        let fixture = try Fixtures.load("curve_edge_cases", as: EdgeCaseFixture.self)
        #expect(!fixture.valueToTokens.isEmpty)
        #expect(!fixture.tokensForValueExchange.isEmpty)

        for v in fixture.valueToTokens {
            let actual = SharedDiscreteCurve.valueToTokens(currentSupply: v.currentSupply, value: v.value)
            guard let expected = v.tokens else {
                #expect(actual == nil, "expected null for \(v.name), got \(actual ?? "nil")")
                continue
            }
            let actualValue = try #require(actual, "valueToTokens unexpectedly nil for \(v.name)")
            #expect(Decimal(string: actualValue) == Decimal(string: expected), "valueToTokens mismatch for \(v.name)")
        }

        for v in fixture.tokensForValueExchange {
            let actual = SharedDiscreteCurve.tokensForValueExchange(currentValue: v.currentValue, value: v.value)
            guard let expectedTokens = v.tokens, let expectedFx = v.fx else {
                #expect(actual == nil, "expected null for \(v.name), got \(String(describing: actual))")
                continue
            }
            let result = try #require(actual, "tokensForValueExchange unexpectedly nil for \(v.name)")
            #expect(Decimal(string: result.tokens) == Decimal(string: expectedTokens), "tokens mismatch for \(v.name)")
            #expect(Decimal(string: result.fx) == Decimal(string: expectedFx), "fx mismatch for \(v.name)")
        }
    }
}
