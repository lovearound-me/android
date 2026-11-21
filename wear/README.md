# Love Around for Wear OS

The Apple Watch app of [lovearound-me/ios](https://github.com/lovearound-me/ios)
(`Watch/`), for Wear OS 3 and newer. Standalone: it needs no phone app.

- **Signing in**: six digits and a QR. The phone's camera opens the phone
  app's «Подключить устройство» (or lovearound.me/login/devices), which
  confirms them; the watch then holds a device token (`dev_…`) of its own,
  which «Устройства» → «Убрать» cuts off. No password on the wrist.
- **«О ком думаешь?»**: «Кому-нибудь на свете» and the people in Settings →
  «Вид»: «Соты» (bubbles that shrink towards the round edge), «Кружочки»,
  «Кружочки с именами», «Список». A tap sends; a held finger shows the whole
  name, letting go sends, sliding away cancels.
- **Hearts in**: FCM data messages to the watch itself
  (`PUT /v1/devices/self/push`, `platform: android`), on the watch's own
  terms from its card on the phone. In front: the edge glows in my color
  and hearts float up; behind: a notification with a wave of hearts.
- **Settings**: «Получать сердечки» (the card's «Принимает»), «Как
  светится», «Вид», «Вибрация» (off: hearts come quietly), «Выйти» (the watch
  leaves the account's devices). An account is deleted on the phone or at
  app.lovearound.me.

The texts, colors and the server's shapes are the phone app's
(`../shared`). Same package as the phone app (`me.lovearound.android`): one
listing on Google Play, a bundle of its own, its build number 100000000
above the phone's.

    ./gradlew :wear:testDebugUnitTest -Proborazzi.test.record=true   # screens → wear/build/outputs/roborazzi
    adb shell am start -n me.lovearound.android/.wear.MainActivity --es demo hive --es lang ru
