JARVIS Android Project - Version 1.0

महत्वपूर्ण:
यह एक शुरुआती Kotlin Android प्रोजेक्ट है, तैयार APK नहीं।
यह अभी बटन दबाकर बोलने वाला assistant है; हमेशा background में 'Hey Jarvis' सुनने वाला wake-word service इसमें शामिल नहीं है।

फोन से APK बनाने का आसान रास्ता:
1. इस ZIP को डाउनलोड करके Extract करें।
2. GitHub पर नया repository बनाकर इस folder की सभी files upload करें।
3. GitHub Actions में Android build workflow जोड़ें या किसी भरोसेमंद Android cloud build service का उपयोग करें।
4. Android Studio उपलब्ध हो तो project खोलकर Gradle Sync करें और Build > Build APK(s) चुनें।

मुख्य फीचर:
- हिंदी speech input (फोन में speech recognition service जरूरी)
- हिंदी Text-to-Speech
- Offline basic commands: WhatsApp, YouTube, camera, settings, Wi-Fi/Bluetooth settings, battery, time, alarm screen, dialer
- Optional online Gemini 2.5 Flash response (internet + Gemini API key जरूरी)

Gemini API key:
https://aistudio.google.com/app/apikey
API key निजी रखें। इस demo में key app की private SharedPreferences में रखी जाती है; यह production-grade secure key management नहीं है। सार्वजनिक APK में अपनी निजी key embed न करें।

सीमाएँ:
- Android app पूरे फोन पर असीमित नियंत्रण नहीं पा सकता।
- WhatsApp संदेश भेजना, दूसरे ऐप के अंदर क्लिक करना, फोन लॉक/unlock करना, और system toggles बदलना अलग permissions/confirmation/OS restrictions के अधीन हैं।
- कॉल कमांड dialer खोलता है; बिना पुष्टि सीधे कॉल नहीं करता।
- Alarm command clock app खोलता है; अभी समय parse करके alarm auto-set नहीं करता।
- Speech input Android speech provider पर निर्भर है; offline recognition तभी चलेगा जब फोन में offline language pack/provider उपलब्ध हो।
- Background always-on wake word और Accessibility automation अगला development चरण है।
