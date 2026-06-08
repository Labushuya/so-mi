package io.somi.rag.embed

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * v0.14.0 M2 — pure-Kotlin WordPiece tokenizer.
 *
 * Reads a HuggingFace `tokenizer.json` (BertTokenizer + WordPiece
 * subset) and produces token IDs compatible with BERT-family
 * embeddings, specifically the multilingual MiniLM L12 v2 model.
 *
 * What this tokenizer supports (the subset paraphrase-multilingual-
 * MiniLM-L12-v2 needs):
 *   - WordPiece model with `##` continuation prefix
 *   - BertNormalizer with case-preserving lowercase=false
 *   - BertPreTokenizer (whitespace + punctuation split)
 *   - TemplateProcessing post-processor adding [CLS] $A [SEP]
 *   - Standard special tokens: [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102
 *
 * What it does NOT support (would need a fuller parser):
 *   - BPE / Unigram / SentencePiece
 *   - Custom regex pre-tokenizers
 *   - PairTemplateProcessing for sentence-pair tasks
 *   - Byte-level byte-pair encoding
 *
 * If we ever swap embedders to bge-m3 / multilingual-e5, this class
 * is replaced not extended.
 *
 * **Threading:** instances are NOT thread-safe. The encode buffer is
 * reused per call; create one instance per dispatcher thread (or
 * pin to a single dispatcher, which is what the [Embedder] does).
 */
class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val unkTokenId: Int,
    private val clsTokenId: Int,
    private val sepTokenId: Int,
    private val padTokenId: Int,
    private val maxInputCharsPerWord: Int,
    private val continuingSubwordPrefix: String,
    private val lowercase: Boolean,
    private val stripAccents: Boolean,
    private val tokenizeChineseChars: Boolean,
    private val maxSeqLength: Int,
) {

    /**
     * Tokenize [text] and emit `[CLS] tokens [SEP]` as token IDs.
     * Truncates to [maxSeqLength] from the right (keep the prefix).
     */
    fun encode(text: String): IntArray {
        val pieces = mutableListOf<Int>()
        pieces += clsTokenId

        // 1. Normalize (Unicode NFKD then optional accent strip / lowercase).
        val normalized = normalize(text)

        // 2. Whitespace tokenize + punctuation split.
        val basicTokens = basicTokenize(normalized)

        // 3. WordPiece greedy match per basic token.
        for (token in basicTokens) {
            if (pieces.size + 1 >= maxSeqLength) break // reserve slot for [SEP]
            wordPieceTokenize(token, pieces)
        }

        if (pieces.size >= maxSeqLength) {
            // Hard truncate — overwrite the last with [SEP].
            pieces[maxSeqLength - 1] = sepTokenId
            return pieces.subList(0, maxSeqLength).toIntArray()
        }
        pieces += sepTokenId
        return pieces.toIntArray()
    }

    private fun normalize(text: String): String {
        var s = text
        if (lowercase) s = s.lowercase()
        if (stripAccents) {
            // NFKD + drop combining marks (Mn category).
            val nfkd = Normalizer.normalize(s, Normalizer.Form.NFKD)
            val sb = StringBuilder(nfkd.length)
            for (cp in nfkd.codePoints()) {
                val type = Character.getType(cp)
                if (type != Character.NON_SPACING_MARK.toInt()) {
                    sb.appendCodePoint(cp)
                }
            }
            s = sb.toString()
        }
        return s
    }

    private fun basicTokenize(text: String): List<String> {
        // BertPreTokenizer behavior: whitespace split then punctuation
        // split (standalone), with optional Chinese-char isolation.
        val tokens = mutableListOf<String>()
        val word = StringBuilder()

        fun flushWord() {
            if (word.isNotEmpty()) {
                tokens += word.toString()
                word.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val cpLen = Character.charCount(cp)
            when {
                Character.isWhitespace(cp) -> flushWord()
                isPunctuation(cp) -> {
                    flushWord()
                    tokens += String(Character.toChars(cp))
                }
                tokenizeChineseChars && isChineseChar(cp) -> {
                    flushWord()
                    tokens += String(Character.toChars(cp))
                }
                else -> word.appendCodePoint(cp)
            }
            i += cpLen
        }
        flushWord()
        return tokens
    }

    private fun wordPieceTokenize(token: String, out: MutableList<Int>) {
        if (token.length > maxInputCharsPerWord) {
            out += unkTokenId
            return
        }

        // Greedy longest-match-first from left.
        val subTokens = mutableListOf<Int>()
        var start = 0
        val n = token.length
        while (start < n) {
            var end = n
            var matched = -1
            while (end > start) {
                var sub = token.substring(start, end)
                if (start > 0) sub = continuingSubwordPrefix + sub
                val id = vocab[sub]
                if (id != null) {
                    matched = id
                    break
                }
                end -= 1
            }
            if (matched == -1) {
                // No piece matched anywhere; the entire word is UNK.
                out += unkTokenId
                return
            }
            subTokens += matched
            start = end
        }
        out += subTokens
    }

    private fun isPunctuation(cp: Int): Boolean {
        // Per BERT: ASCII punctuation 33-47, 58-64, 91-96, 123-126
        // PLUS Unicode P* categories.
        if ((cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126)) {
            return true
        }
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            -> true
            else -> false
        }
    }

    private fun isChineseChar(cp: Int): Boolean {
        // CJK Unified Ideographs ranges per BERT's mBERT impl.
        return (cp in 0x4E00..0x9FFF) ||
            (cp in 0x3400..0x4DBF) ||
            (cp in 0x20000..0x2A6DF) ||
            (cp in 0x2A700..0x2B73F) ||
            (cp in 0x2B740..0x2B81F) ||
            (cp in 0x2B820..0x2CEAF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x2F800..0x2FA1F)
    }

    companion object {
        const val DEFAULT_MAX_SEQ_LENGTH = 128

        /**
         * Parse a HuggingFace `tokenizer.json` from disk and build a
         * runtime-ready tokenizer. Throws if the JSON shape doesn't
         * match the BertTokenizer + WordPiece subset we support.
         */
        fun load(tokenizerJson: File, maxSeqLength: Int = DEFAULT_MAX_SEQ_LENGTH): WordPieceTokenizer {
            val text = tokenizerJson.readText(Charsets.UTF_8)
            val root = JSONObject(text)

            // model.{type, vocab, unk_token, continuing_subword_prefix, max_input_chars_per_word}
            val model = root.getJSONObject("model")
            val type = model.getString("type")
            require(type == "WordPiece") {
                "Expected WordPiece model, got $type. " +
                    "If the embedder changed, swap WordPieceTokenizer for the matching impl."
            }
            val unkToken = model.optString("unk_token", "[UNK]")
            val continuing = model.optString("continuing_subword_prefix", "##")
            val maxInputCharsPerWord = model.optInt("max_input_chars_per_word", 100)
            val vocabJson = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabJson.length())
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                vocab[k] = vocabJson.getInt(k)
            }

            // normalizer.{type, lowercase, strip_accents, handle_chinese_chars}
            val normalizer = root.optJSONObject("normalizer")
            val lowercase = normalizer?.optBoolean("lowercase", false) ?: false
            // strip_accents: null in the JSON means "follow lowercase";
            // explicit true/false honored as-is.
            val stripAccents = when {
                normalizer == null -> false
                normalizer.isNull("strip_accents") -> lowercase
                else -> normalizer.getBoolean("strip_accents")
            }
            val handleChineseChars = normalizer?.optBoolean("handle_chinese_chars", true) ?: true

            // Resolve special token IDs.
            val unkId = vocab[unkToken]
                ?: error("vocab missing unk_token '$unkToken'")
            val clsId = vocab["[CLS]"]
                ?: error("vocab missing [CLS]")
            val sepId = vocab["[SEP]"]
                ?: error("vocab missing [SEP]")
            val padId = vocab["[PAD]"] ?: 0

            Log.i(TAG, "WordPieceTokenizer loaded: vocab=${vocab.size}, lowercase=$lowercase, stripAccents=$stripAccents, cls=$clsId, sep=$sepId")

            return WordPieceTokenizer(
                vocab = vocab,
                unkTokenId = unkId,
                clsTokenId = clsId,
                sepTokenId = sepId,
                padTokenId = padId,
                maxInputCharsPerWord = maxInputCharsPerWord,
                continuingSubwordPrefix = continuing,
                lowercase = lowercase,
                stripAccents = stripAccents,
                tokenizeChineseChars = handleChineseChars,
                maxSeqLength = maxSeqLength,
            )
        }

        private const val TAG = "WordPieceTokenizer"
    }
}
