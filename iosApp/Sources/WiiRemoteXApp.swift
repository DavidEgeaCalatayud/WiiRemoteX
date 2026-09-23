import SwiftUI

@main
struct WiiRemoteXApp: App {
    @StateObject private var viewModel = WiiRemoteViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(viewModel)
        }
    }
}
