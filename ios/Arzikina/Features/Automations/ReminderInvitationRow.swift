import SwiftUI
import UIKit

/// Invitation à activer les rappels d'automatisation, en tête de l'écran Automatisations tant
/// qu'ils ne sont pas autorisés : demande système si elle n'a jamais été faite, sinon lien vers
/// les réglages de notifications d'Arzikina (iOS ne permet plus de redemander).
struct ReminderInvitationRow: View {

    let authorization: ReminderAuthorization
    let onEnable: @MainActor () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bell.badge")
                .font(.title3)
                .foregroundStyle(Brand.primary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text("automations.reminders.title")
                    .font(.subheadline.weight(.semibold))
                Text(messageKey)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button(buttonKey) {
                    if authorization == .denied {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
                    } else {
                        onEnable()
                    }
                }
                .buttonStyle(.bordered)
                .font(.subheadline.weight(.semibold))
            }
        }
        .padding(.vertical, 4)
    }

    private var messageKey: LocalizedStringKey {
        authorization == .denied ? "automations.reminders.denied" : "automations.reminders.message"
    }

    private var buttonKey: LocalizedStringKey {
        authorization == .denied ? "automations.reminders.open_settings" : "automations.reminders.enable"
    }
}
