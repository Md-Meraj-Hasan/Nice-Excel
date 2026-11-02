export function normalizeFormula(formula) {
  let output = '', inDoubleQuotedText = false, inSingleQuotedSheet = false;
  for (let i = 0; i < formula.length;) {
    const ch = formula[i];
    if (ch === '"' && !inSingleQuotedSheet) {
      output += ch;
      if (inDoubleQuotedText && formula[i + 1] === '"') { output += '"'; i += 2; continue; }
      inDoubleQuotedText = !inDoubleQuotedText; i++; continue;
    }
    if (ch === "'" && !inDoubleQuotedText) {
      output += ch;
      if (inSingleQuotedSheet && formula[i + 1] === "'") { output += "'"; i += 2; continue; }
      inSingleQuotedSheet = !inSingleQuotedSheet; i++; continue;
    }
    if (!inDoubleQuotedText && !inSingleQuotedSheet && /[A-Za-z_]/.test(ch)) {
      let end = i + 1;
      while (end < formula.length && /[A-Za-z0-9_.]/.test(formula[end])) end++;
      const token = formula.slice(i, end);
      const upper = token.toUpperCase();
      if ((upper === 'TRUE' || upper === 'FALSE') && !/^\s*\(/.test(formula.slice(end))) {
        output += upper + '()';
      } else output += token;
      i = end; continue;
    }
    output += ch; i++;
  }
  return output;
}
