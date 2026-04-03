# FaceFit AR (Android) – Complete Project Documentation

## 1. Project Summary
**FaceFit AR** is a native Android camera application that applies **real-time facial AR filters** using **Google ML Kit face landmarks** over a **CameraX live preview**.  
The app provides authentication, real-time filter switching, and photo capture with overlay rendering saved to gallery.

### Primary Goals
- Build a smooth real-time face filter experience.
- Improve overlay quality using landmark-based placement, scaling, and rotation.
- Keep UI clean, responsive, and easy to use.
- Save captured media with active filter applied.

## 2. Assignment Terms and Requirement Mapping
This section maps key internship assignment points to this implementation.

### Native Android Development (Kotlin)
- Language: **Kotlin**
- UI: **Jetpack Compose**
- Navigation and screen structure implemented in native Android modules.

### Mandatory Technologies
- **CameraX API**: live camera preview + frame analysis.
- **Google ML Kit**: face detection + landmarks.
- **Firebase Authentication**: email/password + Google sign-in.
- **Firebase Firestore**: user metadata persistence.
- **MVVM Architecture**: ViewModel-driven state handling.

### Core Functional Requirements
- **Authentication System**
  - Login, Signup, Google sign-in available.
  - Input validation implemented (email pattern, password length, confirm password).
- **Real-Time Face Detection**
  - Landmarks extracted continuously from analysis frames.
- **Face Filter Application**
  - Rose crown, animal ears, glasses, mask, sparkles.
  - Dynamic scaling and rotation based on landmark geometry.
- **Media Capture**
  - Capture current preview and save filtered image to gallery.
- **UI/UX Requirements**
  - Full-screen camera preview.
  - Bottom filter carousel with icon + name labels.
  - Clear loading/error/success feedback states.

## 3. Technology Stack

### Android & UI
- **Kotlin**
- **Jetpack Compose** (`Material3`, `Canvas`, composable state)
- **Lifecycle + ViewModel**

### Camera & Vision
- **CameraX**
  - `Preview` for camera feed
  - `ImageAnalysis` for real-time frame processing
- **Google ML Kit Face Detection**
  - Fast performance mode
  - Full landmarks enabled

### Backend Services
- **Firebase Authentication**
  - Email/password auth
  - Google credential flow
- **Firebase Firestore**
  - User profile document storage (`uid`, `email`, `createdAt`)

### Build/Project
- **Gradle (KTS)** Android build system
- Android app module with structured packages for `auth`, `camera`, `viewmodel`, and `ui`

## 4. Architecture Overview (MVVM)

### UI Layer
- `LoginScreen` and `SignUpScreen` for authentication.
- `CameraPreviewScreen` for live AR rendering and user interactions.
- `ProfileScreen` for account-level actions (including sign out).

### ViewModel Layer
- `AuthViewModel`
  - Manages `AuthState` (`Idle`, `Loading`, `Success`, `Error`)
  - Runs validation and triggers Firebase APIs
- `CameraViewModel`
  - Holds selected filter
  - Holds current face list and analyzed image dimensions

### Domain/Processing Layer
- `FaceAnalyzer`
  - Converts CameraX frames to ML Kit input image
  - Emits `faces + width + height`
- `ImageCaptureUtils`
  - Draws filters to bitmap during capture
  - Saves to MediaStore/Gallery

## 5. Real-Time AR Rendering Pipeline
1. Camera frame received by CameraX.
2. Frame analyzed by ML Kit face detector.
3. Landmarks + face bounds pushed to `CameraViewModel`.
4. Compose `Canvas` overlays selected filter on preview.
5. Filter carousel changes selected filter in real time.

### Landmark Usage by Filter
- **Rose Crown**: eye line and forehead offset.
- **Animal Ears**: ear landmarks + eye line orientation.
- **Cool Glasses**: eye centers + eye angle + eye distance.
- **Safety Mask**: nose, mouth, cheek, and ear strap anchors.
- **Sparkles**: decorative random points around face bounds.

### Placement Accuracy Strategy
- Uses **uniform scale + crop offset** to match `PreviewView.ScaleType.FILL_CENTER`.
- Applies x-axis mirroring for front camera consistency.
- Uses same mapping in preview and capture to reduce mismatch.

## 6. Authentication and Validation

### Supported Auth Modes
- Email and Password login
- Email and Password signup
- Google sign-in

### Validation Rules Implemented
- Email required + valid format (`Patterns.EMAIL_ADDRESS`)
- Password required + minimum 6 characters
- Confirm password required + must match

### Error Feedback
- Validation/Firebase errors propagated through `AuthState.Error`
- Displayed in auth screens as visible user error text

## 7. Camera States and UX Feedback
The app includes explicit state feedback aligned to UX wireframe expectations:
- Camera permission request state
- No face detected message
- Saving indicator while capture processing is active
- Save success feedback (`Saved to gallery`)
- Auth loading and error states

## 8. Filters Implemented
- `Normal`
- `Rose Crown`
- `Animal Ears`
- `Cool Glasses`
- `Safety Mask`
- `Sparkles`

### Filter Picker UX
- Bottom rounded translucent panel
- Icon-based filter chips with labels
- Selected filter highlight (size + color emphasis)

## 9. Media Capture and Gallery Storage
When capture is triggered:
1. Current `PreviewView` bitmap is read.
2. Selected filter is redrawn onto bitmap with landmark mapping.
3. Result is saved into `Pictures/FaceFitAR` via MediaStore.
4. Success or failure feedback is shown.

## 10. Performance Considerations
- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to avoid frame queue buildup.
- ML Kit fast mode for lower latency.
- Lightweight procedural overlays in Canvas.
- Capture rendering occurs only on user action.

## 11. Key Files and Module Map
- `auth/AuthViewModel.kt` – auth flows + validation + Firestore user save
- `auth/GoogleAuthHelper.kt` – Google sign-in helper
- `ui/screens/auth/LoginScreen.kt` – login UI
- `ui/screens/auth/SignUpScreen.kt` – signup UI
- `camera/FaceAnalyzer.kt` – ML Kit analyzer
- `viewmodel/CameraViewModel.kt` – camera/filter state
- `camera/FaceOverlayState.kt` – face + image dimensions state model
- `camera/FilterType.kt` – filter enum and labels
- `ui/screens/camera/CameraPreviewScreen.kt` – live preview + overlay + carousel + capture
- `camera/ImageCaptureUtils.kt` – capture filter rendering + gallery save

## 12. Build and Run Instructions
1. Open project in Android Studio / Cursor with Android SDK configured.
2. Ensure Firebase config file and required dependencies are present.
3. Build:
   - `./gradlew :app:assembleDebug` (Linux/macOS)
   - `.\gradlew.bat :app:assembleDebug` (Windows)
4. Run on a physical Android device with camera permission enabled.

## 13. Current Scope and Future Improvements
### Completed Scope
- Real-time filters with landmark alignment
- Authentication and Firestore integration
- Gallery photo capture with overlays
- Submission-grade wireframe and state mapping support

### Future Enhancements
- Optional short video recording with filters
- More realistic texture-based AR assets
- Advanced smoothing/temporal stabilization for landmarks
- Additional profile/user settings and analytics

## 14. Submission Checklist
- Wireframe (Figma/Canva)
- User flow diagram
- This documentation (2–3 pages equivalent)
- APK/AAB
- Demo video (2–3 minutes)
- GitHub repository link (if required by evaluator)

