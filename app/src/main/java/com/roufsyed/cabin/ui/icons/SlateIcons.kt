package com.roufsyed.cabin.ui.icons

/** Every icon of the Slate design, on its 24 x 24 grid unless noted. Geometry is copied from the design. */
object SlateIcons {
    private const val STRIKE = "M4 4l16 16"
    private const val HANDSET =
        "M6 3.5h3.2l1.6 4.2-2.1 1.4a12 12 0 0 0 6.2 6.2l1.4-2.1 4.2 1.6V18a2.5 2.5 0 0 1-2.7 2.5C10.6 19.9 4.1 13.4 3.5 6.2A2.5 2.5 0 0 1 6 3.5z"

    // Status
    val StatusPhone = icon { rect(7f, 3f, 10f, 18f, 2f); path("M11 17.5h2") }
    val StatusPhoneOff = icon { rect(7f, 3f, 10f, 18f, 2f); path("M11 17.5h2"); path(STRIKE) }
    val StatusWifi = icon {
        path("M4 10a11.5 11.5 0 0 1 16 0"); path("M7.5 13.5a6.5 6.5 0 0 1 9 0"); dot(12f, 17.5f, 1.1f)
    }
    val StatusWifiOff = icon {
        path("M4 10a11.5 11.5 0 0 1 16 0"); path("M7.5 13.5a6.5 6.5 0 0 1 9 0"); dot(12f, 17.5f, 1.1f); path(STRIKE)
    }
    val StatusGps = icon {
        path("M12 21c-3.5-3.6-6-6.9-6-10a6 6 0 0 1 12 0c0 3.1-2.5 6.4-6 10z"); circle(12f, 11f, 2f)
    }
    val StatusGpsOff = icon {
        path("M12 21c-3.5-3.6-6-6.9-6-10a6 6 0 0 1 12 0c0 3.1-2.5 6.4-6 10z"); circle(12f, 11f, 2f); path(STRIKE)
    }

    // Navigation
    /** 14 x 18 grid. */
    val TurnRight = icon(14f, 18f) { path("M0.5 17.5V9a4 4 0 0 1 4-4h9"); path("M9.5 1l4 4-4 4") }
    val Mic = icon { rect(9f, 3f, 6f, 11f, 3f); path("M5.5 11.5a6.5 6.5 0 0 0 13 0"); path("M12 18v3") }
    val House = icon { path("M4 11.5L12 4l8 7.5"); path("M6.5 10v9.5h11V10") }
    val Briefcase = icon {
        rect(3.5f, 7.5f, 17f, 12f, 2f); path("M9 7.5V5.5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2")
    }
    val ChevronRight = icon { path("M9.5 5.5L16 12l-6.5 6.5") }
    val ChevronLeft = icon { path("M14.5 5.5L8 12l6.5 6.5") }
    /** For the bar over other apps. Not in the design, which has no Back button: its left chevron with a shaft. */
    val Back = icon { path("M11 5.5L4.5 12l6.5 6.5"); path("M4.5 12h15") }
    /** Not in the design either: a screen parted down the middle, for two apps side by side. */
    val Split = icon { rect(3.5f, 5f, 17f, 14f, 2f); path("M12 5v14") }

    // Transport
    val Previous = icon { path("M18 6v12l-9-6z"); path("M6 6v12") }
    val Next = icon { path("M6 6v12l9-6z"); path("M18 6v12") }
    val Pause = icon { path("M8.5 6v12"); path("M15.5 6v12") }
    val Play = icon { path("M9 6v12l9.5-6z") }
    val Bars = icon { path("M6 10v8"); path("M12 5v13"); path("M18 12v6") }

    // Steppers
    val Minus = icon { path("M5 12h14") }
    val Plus = icon { path("M12 5v14"); path("M5 12h14") }
    val Sun = icon {
        circle(12f, 12f, 4f)
        path("M12 2.5v2.5"); path("M12 19v2.5"); path("M2.5 12H5"); path("M19 12h2.5")
        path("M5.3 5.3l1.8 1.8"); path("M16.9 16.9l1.8 1.8"); path("M5.3 18.7l1.8-1.8"); path("M16.9 7.1l1.8-1.8")
    }
    val Moon = icon { path("M20.5 13.2A8.6 8.6 0 1 1 10.8 3.5a7.2 7.2 0 0 0 9.7 9.7z") }
    val Speaker = icon {
        path("M3.5 9.5v5H7l5 4V5.5l-5 4z"); path("M15.5 9a4 4 0 0 1 0 6"); path("M18.2 6.5a7.5 7.5 0 0 1 0 11")
    }
    val SpeakerMuted = icon { path("M3.5 9.5v5H7l5 4V5.5l-5 4z"); path("M16 9.5l5 5"); path("M21 9.5l-5 5") }

    // Rail and app tiles
    val Handset = icon { path(HANDSET) }
    val Link = icon {
        path("M11.5 15.5h-7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v1.5"); rect(14f, 10f, 7.5f, 11f, 1.5f)
    }
    val AppsGrid = icon {
        rect(4f, 4f, 6.5f, 6.5f, 1.5f); rect(13.5f, 4f, 6.5f, 6.5f, 1.5f)
        rect(4f, 13.5f, 6.5f, 6.5f, 1.5f); rect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f)
    }
    val Browser = icon {
        circle(12f, 12f, 9f); path("M3 12h18")
        path("M12 3c2.5 2.6 3.8 5.7 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.7-3.8-9s1.3-6.4 3.8-9z")
    }
    val Clock = icon { circle(12f, 12f, 9f); path("M12 7v5l3.5 2") }
    val Files = icon {
        path("M3.5 7.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v7.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    }
    val Gallery = icon { rect(3.5f, 5f, 17f, 14f, 2f); circle(9f, 10f, 1.5f); path("M20.5 15.5L16 11l-7 8") }
    val Maps = icon { path("M12 3l7.5 17.5L12 16.5l-7.5 4z") }
    val Music = icon { path("M9 17.5v-12l10-2v12"); circle(6.5f, 17.5f, 2.5f); circle(16.5f, 15.5f, 2.5f) }
    val Radio = icon {
        rect(3f, 9f, 18f, 11f, 2f); path("M7 9l9-5"); circle(8.5f, 14.5f, 2.5f)
        path("M14.5 12.5H18"); path("M14.5 16.5H18")
    }
    val Settings = icon {
        path("M4 7h10"); path("M18 7h2"); circle(16f, 7f, 2f)
        path("M4 12h2"); path("M10 12h10"); circle(8f, 12f, 2f)
        path("M4 17h8"); path("M16 17h4"); circle(14f, 17f, 2f)
    }
    val Sound = icon {
        path("M6 4v5"); path("M6 13v7"); circle(6f, 11f, 2f)
        path("M12 4v9"); path("M12 17v3"); circle(12f, 15f, 2f)
        path("M18 4v2"); path("M18 10v10"); circle(18f, 8f, 2f)
    }
    val Video = icon { circle(12f, 12f, 9f); path("M10 8.5v7l5.5-3.5z") }
    val Weather = icon { path("M7 18.5a4 4 0 0 1-.6-7.96A5.5 5.5 0 0 1 17 9.5a4.5 4.5 0 0 1 0 9z") }
    /** The ring behind a one-letter monogram. */
    val Ring = icon { circle(12f, 12f, 9f) }

    /** A ring with a dot in it: the one chosen among several, as on a radio button. */
    val RingChosen = icon {
        circle(12f, 12f, 9f)
        dot(12f, 12f, 4f)
    }

    // Tray and keys
    val Message = icon { path("M4 5.5h16v10.5H10l-4.5 4v-4H4z") }
    val Mode = icon {
        path("M4.5 12a7.5 7.5 0 0 1 12.9-5.2"); path("M17.5 3.5V7H14")
        path("M19.5 12a7.5 7.5 0 0 1-12.9 5.2"); path("M6.5 20.5V17H10")
    }
}
