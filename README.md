# kmp-ios-probe

A minimal Kotlin Multiplatform + Compose Multiplatform app used to check that a toolchain works end to end
when the development machine is Windows and the only Mac is a CI runner:

- shared Kotlin compiled and tested on Windows/Linux (`:shared:desktopTest`, `:shared:compileKotlinIosSimulatorArm64`)
- Xcode project generated with XcodeGen from `iosApp/project.yml`
- app built for the iOS simulator without an Apple account, launched, and screenshots uploaded as artifacts

Run: Actions → "iOS simulator build" → Run workflow. Screenshots appear in the `ios-shots` artifact.
