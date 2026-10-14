import { readFileSync, writeFileSync } from 'fs';

// Replace ALL remaining U+201C and U+201D with straight double-quotes
// in all Kotlin source files that need it.
// Also handles embedded „..." patterns that need \" escaping.

function fixAllCurlyQuotes(path) {
  let content = readFileSync(path, 'utf8');

  // First pass: replace all U+201C and U+201D with straight "
  const pass1 = content.replace(/[“”]/g, '"');

  if (pass1 === content) {
    console.log(path + ': no curly quotes found');
    writeFileSync(path, pass1, 'utf8');
    return;
  }

  const count = (content.match(/[“”]/g) || []).length;
  console.log(path + ': replacing ' + count + ' curly quotes');

  // Second pass: fix any „text" patterns where the closing " is now straight
  // and would break Kotlin string parsing.
  // Strategy: on each line, find patterns like „word" where the " is inside
  // a string literal (i.e., after an odd number of " from the left).
  const lines = pass1.split('\n');
  let fixedLines = 0;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!line.includes('„')) continue; // No „, nothing to fix

    // Count straight quotes to determine if we're in a string
    // For each „...\" pattern, the closing " needs to be escaped
    // Simple heuristic: any " that follows „ and comes before the line's
    // outer string close should be escaped.
    // We'll use a state machine: track if we're inside a string.
    let inString = false;
    let result = '';
    let j = 0;
    while (j < line.length) {
      const c = line[j];
      const code = line.charCodeAt(j);
      if (code === 0x22) { // straight "
        inString = !inString;
        result += c;
      } else if (code === 0x201E && inString) {
        // Low-9 quote „ — this opens a German quotation in content
        // The next " will be the closing quote, needs to be escaped
        result += c;
        j++;
        // Consume until we find the matching "
        while (j < line.length) {
          const cc = line[j];
          const ccode = line.charCodeAt(j);
          if (ccode === 0x22) {
            result += '\\"'; // escape it
            j++;
            break;
          }
          result += cc;
          j++;
        }
        continue;
      } else {
        result += c;
      }
      j++;
    }
    if (result !== line) {
      console.log('  Line ' + (i+1) + ': fixed „...\" escaping');
      console.log('    Before: ' + line.trim().substring(0, 100));
      console.log('    After:  ' + result.trim().substring(0, 100));
      lines[i] = result;
      fixedLines++;
    }
  }

  writeFileSync(path, lines.join('\n'), 'utf8');
  console.log(path + ': done (' + fixedLines + ' lines had „...\" pattern)');
}

fixAllCurlyQuotes('android/app/src/main/kotlin/io/somi/app/SettingsScreen.kt');
fixAllCurlyQuotes('android/app/src/main/kotlin/io/somi/app/settings/ModelCatalogScreen.kt');
