# Contributing Guidelines

## Development Workflow
1. **Branching**: Use feature branches named `feature/<short-description>` or `fix/<short-description>`.
2. **Code Style**: Strictly adhere to Android Kotlin style guides and Material Design 3 guidelines.
3. **Automated Verification**:
   - Run `gradle :app:testDebugUnitTest` before opening pull requests.
   - Verify Roborazzi screenshot tests when making UI modifications: `gradle :app:verifyRoborazziDebug`.
4. **Permissions & Security**: Never introduce workarounds that attempt to bypass Android camera privacy indicators, keyguard security policies, or permission models.
