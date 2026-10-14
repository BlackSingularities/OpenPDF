import SwiftUI
import Shared

@main
struct OpenPDFApp: App {
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
                // Plik PDF otwarty z innej aplikacji albo z Plików.
                .onOpenURL { url in MainViewControllerKt.openUrl(url: url) }
        }
        .onChange(of: phase) { p in
            if p == .active { MainViewControllerKt.onResume() }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
