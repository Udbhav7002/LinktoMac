// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LinkToMac",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "LinkToMac", targets: ["LinkToMac"])
    ],
    targets: [
        .executableTarget(
            name: "LinkToMac",
            path: "Sources/LinkToMac"
        )
    ]
)
