# Account-Specific Biometric Authentication Plan

Update the biometric authentication system to be account-specific (resident ID style), allowing multiple accounts to have independent biometric configurations stored in a local Room database.

## Proposed Changes

### [Data Models & Persistence]

#### [MODIFY] [BiometricSetting.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/BiometricSetting.java)
- Change the `@PrimaryKey` from a hardcoded `id` to `email` (or `username`).
- Keep `isEnabled`, `savedEmail`, and `savedPassword`.

#### [MODIFY] [BiometricDao.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/local/BiometricDao.java)
- Update `getSetting()` to `getSettingByEmail(String email)`.
- Update `updateEnabledStatus()` to `updateEnabledStatus(String email, boolean enabled)`.
- Add `getAllEnabledSettings()` to potentially support account selection if needed.

### [Utilities]

#### [MODIFY] [SessionManager.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/utils/SessionManager.java)
- Refactor biometric methods to accept an `email` parameter.
- `setBiometricEnabled(String email, boolean enabled)`
- `isBiometricEnabled(String email)`
- `saveUserCredentials(String email, String password)`
- `syncBiometricsFromDb(String email, Runnable onComplete)`
- Add `getLastUsedEmail()` and `setLastUsedEmail(String email)` to handle the default biometric user.

### [User Interface]

#### [MODIFY] [LoginActivity.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/ui/auth/LoginActivity.java)
- Update `biometricBtn` logic:
    - If email field is empty, try to use the last logged-in user's biometric data.
    - If email field is not empty, check if biometrics are enabled specifically for that account.
- Update `viewModel.getLoginSuccess()` observer to save credentials per account.

#### [MODIFY] [SecurityPrivacyFragment.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/ui/profile/SecurityPrivacyFragment.java)
- Ensure biometrics are toggled for the *currently logged-in* user's email.
- Fetch the email from `UserDao` or `SessionManager`.

## Verification Plan

### Automated Tests
- N/A (Project seems to rely on manual verification)

### Manual Verification
1.  **Account 1:** Log in, enable biometrics in Security & Privacy.
2.  **Account 2:** Log out, log in with a different account, enable biometrics.
3.  **Biometric Login:**
    - Log out.
    - Enter Account 1 email, click biometric button -> Should log in as Account 1.
    - Log out.
    - Enter Account 2 email, click biometric button -> Should log in as Account 2.
    - Log out.
    - Leave email empty, click biometric button -> Should log in as the last used account.
