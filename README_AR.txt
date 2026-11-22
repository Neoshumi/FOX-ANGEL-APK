FOX ANGEL — حزمة بناء APK الجاهزة

هذه الحزمة فيها مشروع Android وملف بناء آلي. الناتج المطلوب هو:
app-debug.apk

طريقة استخراج APK بأقل خطوات (من الهاتف أو الكمبيوتر):
1) فك ضغط FOX_ANGEL_BUILD_READY.zip.
2) افتح github.com وأنشئ Repository جديد باسم FOX-ANGEL (اختَر Public أو Private).
3) ارفع محتويات مجلد FOX_ANGEL_Android_Project إلى جذر الـ Repository، بما فيها المجلد المخفي .github.
4) افتح تبويب Actions ثم اختر "Build FOX ANGEL APK" واضغط Run workflow.
5) بعد ما تنجح العملية، افتح نتيجة التشغيل ونزّل Artifact اسمه FOX-ANGEL-debug-apk.
6) فك ضغط الـ Artifact؛ تلقى app-debug.apk. افتحه في هاتف Android وثبّته.

مهم:
- هذه حزمة بناء آلي وليست APK جاهزاً بحد ذاتها.
- البناء الآلي يحتاج حساب GitHub واتصال إنترنت. لا يلزم Android Studio.
- لم يتم بناء APK أو اختباره في هذه البيئة لعدم توفر Android SDK وGradle.
- التطبيق يتصل بعنوان FOX الافتراضي 192.168.0.178، وتدخل REST API KEY داخل التطبيق.
- الهاتف وجهاز FOX يلزم يكونوا على نفس الشبكة أو بيناتهم اتصال شبكي يسمح بالوصول.
- زر V يبدأ جلسة تعرّف صوتي واحدة، وليس استماعاً مستمراً في الخلفية.
