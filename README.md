# Lupin

A set of apps related to installing and updating apps:

* installer: Used to install new apps during SetupWizard
* updater: Used for regular background auto-updates

Code in the `shared` module is used by both apps.

## Notes on new install API introduced with Android 14

These notes can be removed once we implemented the features below.

### Gentle updates

Methods such as [`commitSessionAfterInstallConstraintsAreMet()`](https://developer.android.com/reference/android/content/pm/PackageInstaller#commitSessionAfterInstallConstraintsAreMet(int,%20android.content.IntentSender,%20android.content.pm.PackageInstaller.InstallConstraints,%20long))
can be used to only update apps when certain constraints are met.
[`GENTLE_UPDATE`](https://developer.android.com/reference/android/content/pm/PackageInstaller.InstallConstraints#GENTLE_UPDATE)
currently requires that the user is not interacting with the app.
However, to use these new methods will throw a security exception:

> if the given packages' installer of record doesn't match the caller's own package name
> or the installerPackageName set by the caller doesn't match the caller's own package

So gentle updates only work when we are already are the packages' installer of record
which may not be the case.
Also, we need to check if the app is already installed or if it is being updated,
because if it isn't installed,
the installer of record doesn't exist and will also throw an exception.

After taking all these checks into account,
it seems that even the gentle check that should only check for interaction
[requires the device to be idle](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/GentleUpdateHelper.java;l=56;drc=21fccb8e6f15d539c8fef1f2014651e6a36038b6)
for the check to succeed.
So far, it wasn't possible to make the check succeed within a two minute timeout
when the app to update was briefly in the foreground when the update should be applied.
Then, this requires special handling of sessions whose timeout has expired.

### User Preapproval

Our current `ApkInstaller` assumes that the APK was already downloaded when we install.
The pre-approval can only be done once we have an installer session.
Managing installer sessions and taking care that we don't have too many already is a pain.

Therefore, making use of [pre-approval](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#requestUserPreapproval(android.content.pm.PackageInstaller.PreapprovalDetails,%20android.content.IntentSender))
would require a complete refactoring of how we install APKs.
We'd need to create a session as soon as we know that there's an update,
then wait for user approval (if needed) and only then download and install the APK.
We'd still need to handle edge cases like the situation having changes after download
and still needing manual confirmation.

The only benefit here is that we only need to download the APK after we already have the approval.
Since in the majority of cases the user confirmation should be a formality,
we would not win very much.
A light benefit is that 14 seems to have fixed
the tap outside confirmation dialog bug for pre-approval,
but not for the normal approval.
