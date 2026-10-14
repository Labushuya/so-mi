import { readFileSync, writeFileSync } from 'fs';

// Fix files where U+201E (low-9 open) + content + U+0022 (straight close)
// The closing " was converted from U+201D by our earlier fix, but it
// now acts as string terminator in Kotlin. Escape it as \".
// Pattern: after U+201E, find the matching straight " and escape it.

function fixFile(path) {
  let content = readFileSync(path, 'utf8');
  // U+201E is the low-9 opening guillemet „
  // We need to find „...\" patterns where the closing " is straight
  // Strategy: in each line, if there's a „ followed later by ",
  // the " needs to be escaped as \"
  const lines = content.split('\n');
  let changed = 0;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.includes('„')) {
      // Find all „...\" sequences
      let newLine = line;
      // Replace " that follows „...  with \"
      // but only when it's inside a string (has a preceding unclosed ")
      // Simpler: just replace the pattern „<text>" with „<text>\"
      newLine = newLine.replace(/„([^"]*?)"/g, '„$1\\"');
      if (newLine !== line) {
        console.log(`Line ${i+1}: fixed „...\" pattern`);
        console.log(`  Before: ${line.trim()}`);
        console.log(`  After:  ${newLine.trim()}`);
        lines[i] = newLine;
        changed++;
      }
    }
  }
  if (changed > 0) {
    writeFileSync(path, lines.join('\n'), 'utf8');
    console.log(`${path}: fixed ${changed} line(s)`);
  } else {
    console.log(`${path}: no changes needed`);
  }
}

fixFile('android/app/src/main/kotlin/io/somi/app/SettingsScreen.kt');
fixFile('android/app/src/main/kotlin/io/somi/app/settings/ModelCatalogScreen.kt');
