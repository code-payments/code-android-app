import Foundation
import Testing

/// The canonical cross-platform vectors live in the orchestrator repo (`code/test-vectors/`) and are
/// copied here on update, the same arrangement both apps use. Loading them through `Bundle.module`
/// means a fixture the test target failed to copy fails loudly instead of silently skipping.
enum Fixtures {

    static func load<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try #require(
            Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures"),
            "missing fixture \(name).json"
        )
        return try JSONDecoder().decode(type, from: Data(contentsOf: url))
    }
}

extension Data {

    /// The fixtures carry bytes as lowercase hex.
    init?(hex: String) {
        guard hex.count.isMultiple(of: 2) else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }
}
