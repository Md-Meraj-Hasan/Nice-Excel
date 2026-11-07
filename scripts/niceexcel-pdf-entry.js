import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';

(() => {
  const rgb = (value, fallback) => {
    let hex = String(value || '').trim().replace(/^#/, '');
    if (hex.length === 8) { if (hex.slice(0, 2).toLowerCase() === '00') return null; hex = hex.slice(-6); }
    if (hex.length === 3) hex = hex.split('').map(ch => ch + ch).join('');
    if (!/^[0-9a-f]{6}$/i.test(hex)) return fallback;
    return [0, 2, 4].map(index => parseInt(hex.slice(index, index + 2), 16));
  };
  const cellText = cell => {
    let value = cell?.calculated_value ?? cell?.display_value ?? cell?.value ?? '';
    if (Array.isArray(value)) value = value.map(item => Array.isArray(item) ? item.join(' ') : item).join(' ');
    return value == null ? '' : String(value);
  };
  const styleCell = (target, cell) => {
    const style = cell?.style || {};
    if (style.fill_color || style.fillColor) { const fill = rgb(style.fill_color || style.fillColor, null); if (fill) target.cell.styles.fillColor = fill; }
    if (style.font_color || style.text_color || style.fontColor) { const text = rgb(style.font_color || style.text_color || style.fontColor, [20, 25, 35]); if (text) target.cell.styles.textColor = text; }
    if (style.bold && style.italic) target.cell.styles.fontStyle = 'bolditalic';
    else if (style.bold) target.cell.styles.fontStyle = 'bold';
    else if (style.italic) target.cell.styles.fontStyle = 'italic';
    if (style.font_size) target.cell.styles.fontSize = Math.max(5, Math.min(18, Number(style.font_size) || 9));
    if (style.horizontal && ['left', 'center', 'right'].includes(style.horizontal)) target.cell.styles.halign = style.horizontal;
  };

  window.NiceExcelPDF = {
    createPdf(options) {
      const format = options.paper || 'A4';
      const orientation = options.orientation || 'landscape';
      const doc = new jsPDF({ orientation, unit: 'pt', format: format.toLowerCase(), compress: true });
      const margin = options.margins === 'narrow' ? 18 : options.margins === 'wide' ? 56 : 32;
      const baseFont = options.scale === 'fit-sheet' ? 5.5 : options.scale === 'fit-width' ? 7.5 : 9;
      const padding = options.scale === 'fit-sheet' ? 1 : 3;
      const lineColor = options.gridlines ? [155, 165, 178] : [255, 255, 255];

      (options.sheets || []).forEach((sheet, sheetIndex) => {
        if (sheetIndex) doc.addPage(format.toLowerCase(), orientation);
        let startY = margin;
        if (options.showSheetName !== false) {
          doc.setFont('helvetica', 'bold');
          doc.setFontSize(13);
          doc.setTextColor(30, 40, 55);
          doc.text(String(sheet.name || 'Sheet'), margin, startY + 13);
          startY += 24;
        }
        const toRow = row => [String(row.rowNumber ?? ''), ...(row.cells || []).map(cellText)];
        const head = options.repeatHeader && sheet.headerRow ? [toRow(sheet.headerRow)] : [];
        const body = (sheet.rows || [])
          .filter(row => !(options.repeatHeader && sheet.headerRow && row.rowNumber === sheet.headerRow.rowNumber))
          .map(toRow);
        autoTable(doc, {
          head,
          body,
          startY,
          margin: { top: margin, right: margin, bottom: margin, left: margin },
          theme: 'grid',
          styles: {
            font: 'helvetica',
            fontSize: baseFont,
            cellPadding: padding,
            overflow: 'linebreak',
            valign: 'top',
            lineColor,
            lineWidth: options.gridlines ? 0.45 : 0,
            textColor: [25, 32, 44],
          },
          headStyles: { fillColor: [225, 233, 243], textColor: [25, 32, 44], fontStyle: 'bold' },
          columnStyles: { 0: { cellWidth: 28, halign: 'right', textColor: [100, 110, 125] } },
          showHead: options.repeatHeader ? 'everyPage' : 'never',
          horizontalPageBreak: true,
          horizontalPageBreakRepeat: 0,
          rowPageBreak: 'avoid',
          didParseCell(data) {
            if (data.section === 'body') {
              const sourceRow = (sheet.rows || []).filter(row => !(options.repeatHeader && sheet.headerRow && row.rowNumber === sheet.headerRow.rowNumber))[data.row.index];
              if (sourceRow && data.column.index > 0) styleCell(data, sourceRow.cells?.[data.column.index - 1]);
            }
            if (data.section === 'head' && sheet.headerRow && data.column.index > 0) {
              styleCell(data, sheet.headerRow.cells?.[data.column.index - 1]);
            }
          },
        });
      });
      return doc.output('blob');
    },
  };
})();
