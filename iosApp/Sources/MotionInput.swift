import CoreMotion
import Foundation

struct MotionSample {
    let accelerationXG: Float
    let accelerationYG: Float
    let accelerationZG: Float

    let gyroXRadPerSec: Float
    let gyroYRadPerSec: Float
    let gyroZRadPerSec: Float

    let yawRadians: Float
    let pitchRadians: Float
}

final class MotionInput {
    var onSample: ((MotionSample) -> Void)?

    private let manager = CMMotionManager()
    private(set) var latestSample: MotionSample?

    func start() {
        guard manager.isDeviceMotionAvailable else { return }

        manager.deviceMotionUpdateInterval = 0.01
        manager.startDeviceMotionUpdates(
            using: .xArbitraryZVertical,
            to: .main
        ) { [weak self] motion, _ in
            guard let self, let motion else { return }

            let sample = MotionSample(
                accelerationXG: Float(motion.gravity.x + motion.userAcceleration.x),
                accelerationYG: Float(motion.gravity.y + motion.userAcceleration.y),
                accelerationZG: Float(motion.gravity.z + motion.userAcceleration.z),
                gyroXRadPerSec: Float(motion.rotationRate.x),
                gyroYRadPerSec: Float(motion.rotationRate.y),
                gyroZRadPerSec: Float(motion.rotationRate.z),
                yawRadians: Float(motion.attitude.yaw),
                pitchRadians: Float(motion.attitude.pitch)
            )

            latestSample = sample
            onSample?(sample)
        }
    }

    func stop() {
        manager.stopDeviceMotionUpdates()
    }
}
