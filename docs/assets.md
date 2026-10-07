# Asset provenance

The visual reference is NothingCardLab 3.4.2 and Nothing Weather 3.1.7, extracted from the official Phone (3) full OTA `Metroid_B4.1-260814-1733`.

The donor ZIP came from [Android's OTA delivery service](https://android.googleapis.com/packages/ota-api/package/1825643456d6973c5ea22aff7c29d871cc071c04.zip). Its payload SHA-256 is `77dab4a464719d793b4ccdb3f75ed4d324632d66096705a037cbe0e580b7fe5a`, which matched the OTA payload properties. The complete ZIP signature was not independently verified.

Apktool decoded the widget resources and smali. JADX provided a Java reference, but reported 115 errors; source comparisons used smali when the Java view was incomplete. The weather APK's signing certificate SHA-256 is `8a508115864dc16731c003f2d7775fd721a7e931e03e502873dcef0f1f9add73`.

The app contains selected drawable resources, strings, colors, and fonts alongside the port's Java implementation. It does not contain the original APK's DEX or native libraries. The full firmware, extracted partition images, decompiled application, and private signing key are excluded from this repo.

[Imported resource paths](../source/imported-resources.json) list the resources copied by `tools/prepare_app.py`. The NType82 Regular and Headline fonts are also bundled for static text and font comparisons. [Widget variants](../source/widget-variants.json) list the 31 providers and their default grid sizes.

To regenerate resources locally, place the decoded widget resources at `analysis/widgets-decoded/res` and decoded weather resources at `analysis/weather-latest-decoded/res`, then run:

```bash
python3 tools/prepare_app.py
```

The importer replaces `app/res` and regenerates the manifest, registry, and caption data. Normal builds use the checked-in resources when the donor workspace is absent.

Nothing's artwork and fonts remain the property of their respective owners. This repo does not claim ownership of those assets or declare a project-wide license for them.
