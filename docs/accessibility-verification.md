# Accessibility verification

Compose controls use semantic labels, content descriptions, and test tags where those make user actions unambiguous. Verification covers Home, Calendar, Review, Search, navigation, and common actions through Compose UI tests.

Before release, test with TalkBack enabled on the API 36 AVD:

1. Traverse the capture field, primary navigation, lists, dialogs, and destructive confirmations.
2. Confirm that icon-only controls have useful names and disabled/loading states are announced.
3. Check that text and glass surfaces retain readable contrast in light and dark themes.
4. Verify tap targets and keyboard navigation on the portrait phone layout.

Accessibility checks must use generic test content and never preserve personal data in screenshots, logs, or fixtures.
