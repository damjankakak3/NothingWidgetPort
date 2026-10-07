# NothingWidget

NothingWidget is a native Android app for people who want Nothing's local widget designs in a standard launcher. The repository is named NothingWidgetPort; the app label is NothingWidget and its package is `dev.dipa.widgets`.

## Scope

Support the local widget families represented in NothingCardLab 3.4.2 and Nothing Weather 3.1.7. Preserve their visual proportions and artwork within standard Android AppWidgets. AI generation, community feeds, shared-widget accounts, and Nothing CardService are outside the app's scope.

Weather uses a disclosed forecast service. Other data stays on the device. Request permissions when the user sets up the feature that needs them.

## Appearance and behavior

Use the original Nothing artwork, dot fonts, and red accents for widgets. Use standard Android controls for setup and permission screens. Show real widget previews and group variants by function.

Keep ordinary widgets free of a resident service. Steps use an opt-in hardware-counter service; live compass widgets use a user-started foreground service that pauses sensing while the phone is locked or screen-off. Media uses Android's session callbacks. Explain tap-driven pages and visible compass or breathing sessions in setup. A stopped compass retains its last bearing. Setup and the notification must distinguish live sensing from a stored reading.

## Accessibility

Use meaningful content descriptions, scalable setup text, sufficient contrast, and touch targets of at least 48 dp. Respect Android's animation setting. Keep setup usable with TalkBack and label widget actions.
