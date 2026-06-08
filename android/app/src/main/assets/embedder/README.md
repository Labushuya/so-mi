# Embedder assets

In dieses Verzeichnis gehört (optional) `tokenizer.json` der
`paraphrase-multilingual-MiniLM-L12-v2`-Variante. Wenn die Datei
mit der erwarteten SHA-256 hier liegt, kopiert die App sie beim
Erststart aus dem APK-Asset in den Cache und überspringt den
Tokenizer-Netzwerk-Download komplett — der Erststart kann dann
auch offline tokenisieren, lange bevor `model.onnx` (~470 MB)
über WLAN angekommen ist.

**Nicht eingecheckt heute:** die Datei ist ~9.5 MB und blasen den
Repo unnötig auf, solange wir sie nicht brauchen. Wer den Asset-
Seed-Pfad nutzen will, lädt einmalig:

```
curl -L \
  https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json \
  -o android/app/src/main/assets/embedder/tokenizer.json
```

und verifiziert per `sha256sum`:

```
2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8  tokenizer.json
```

Wenn die Datei fehlt, fällt
`EmbeddingModelDownloadWorker.trySeedFromAsset` still durch und der
Tokenizer kommt wie gehabt über die Mirror-Liste in
`EmbeddingModelCatalog` aus dem Netz.
