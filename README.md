# Love Around for Android and Wear OS

Tap a circle on your phone, and someone you're thinking of gets a bare
"someone is thinking of you": no name, no message. When someone thinks of
you, the screen edges glow in your color and hearts float up. You never
learn who it was.

The Android twin of the iPhone app ([lovearound-me/ios](https://github.com/lovearound-me/ios)):
the same screens, the same server ([lovearound-me/server](https://github.com/lovearound-me/server),
`docs/schema.md`), the same texts (`app/src/main/assets/i18n`, keyed by the
Russian, copied from the iPhone app's `i18n/`).

## The phone app (`app/`)

Kotlin and Jetpack Compose, one activity, package `me.lovearound.android`,
Android 9 and newer.

- **Sign in**: Google through Credential Manager (Google's account chooser
  over the app, its ID token traded for a Firebase session), email and a
  password in the app, and Apple through the site's sign-in page
  (lovearound.me/signin) in a browser tab: the page seals the session to a
  one-time key of the app and hands it back as `lovearound://signin?…`
  (`SiteSignIn.kt`). Firebase Auth is spoken over its REST API, as in the
  Mac and iPhone apps; only the refresh token is kept, in the app's own
  storage, left out of backups.
- **«Сердечки»**: «Кому-нибудь» (once an hour) and the close people in
  circles three to a row, «＋», «Вся книга». A tap sends.
- **«Книга»**: «Рядом» in its order («Порядок»: drag by the handle), the
  others, search; a person's card; adding by a Love ID link.
- **«Игрушки»**: the account's devices, pairing by six digits, a board of
  one's own with its token, a device's card.
- **«Настройки»**: account, who can reach this phone (anyone, quiet hours),
  the glow's color, the notification's look, receiving here, pause.
- **Hearts in**: the server's WebSocket while the app is in front; Firebase
  Cloud Messaging data messages behind (`Messaging` in `Push.kt`), written
  into a notification by the app, new emoji each time.

## The watch app (`wear/`)

See [wear/README.md](wear/README.md).

## Build and check

    ./gradlew assembleDebug testDebugUnitTest
    scripts/screenshots.sh ru build/shots      # every demo screen from a running emulator

`--es demo <name>` opens a screen with made-up people and no network (see
`Demo.kt`); a debug build also takes `--es token <Firebase ID token>` to
sign in for an hour (`tools/test-id-token.py anna` in the lovearound folder).

Release builds are signed with the Google Play upload key, which is never
in this repo: `keystore.properties` on the machine that builds, or the
`UPLOAD_*` environment variables in CI. The debug key is committed on
purpose, so Google sign-in (which checks the key) works in every debug build.

## License

MIT
