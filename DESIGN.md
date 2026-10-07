# Design system

NothingWidget follows the original Nothing styling. Visual references are the NothingCardLab 3.4.2 XML layouts, drawable resources, and font files imported from the donor workspace.

Use the original NDot57 and Lettera fonts for widget artwork. Use the Android system font for setup, permissions, errors, and navigation. Preserve the donor's widget-specific backgrounds, dials, hands, and breathing artwork. Setup screens follow Android's light/dark system theme and use a muted red accent. Previews have clear labels and show the same RemoteViews that the launcher hosts. Group variants by function in expandable sections; avoid eagerly creating every preview.

Native-host limitations require tap-driven page changes and visible activities for live compass and breathing sessions. Standard widgets cannot host Nothing's arbitrary view classes. No general preview animations or idle rendering loops.
