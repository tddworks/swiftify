// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SwiftifyDemo",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .executable(name: "SwiftifyDemo", targets: ["SwiftifyDemo"])
    ],
    targets: [
        .executableTarget(
            name: "SwiftifyDemo",
            dependencies: [],
            path: "macApp",
            exclude: [],
            swiftSettings: [
                .unsafeFlags([
                    "-F", "../build/bin/macosArm64/releaseFramework",
                    "-framework", "SampleKit"
                ])
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-F", "../build/bin/macosArm64/releaseFramework",
                    "-framework", "SampleKit",
                    "-rpath", "@executable_path/../Frameworks"
                ])
            ]
        )
    ]
)
