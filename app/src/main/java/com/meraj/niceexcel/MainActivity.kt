package com.meraj.niceexcel

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.InputType
import java.util.Locale

class MainActivity : Activity() {
    private val rows = 40
    private val cols = 12
    private val data = mutableMapOf<String, String>()
    private var selected = "A1"
    private lateinit var grid: LinearLayout
    private lateinit var address: TextView
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var csvPending = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(22, 32, 48)
        window.navigationBarColor = Color.BLACK
        data["A1"]="Item"; data["B1"]="Qty"; data["C1"]="Price"; data["D1"]="Total"
        data["A2"]="Notebook"; data["B2"]="3"; data["C2"]="12.5"; data["D2"]="=B2*C2"
        data["A3"]="Pen"; data["B3"]="5"; data["C3"]="2"; data["D3"]="=B3*C3"
        data["A4"]="Grand Total"; data["D4"]="=SUM(D2:D3)"
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(244,247,250)) }
        val top = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(14,10,10,10); setBackgroundColor(Color.rgb(22,32,48)) }
        top.addView(TextView(this).apply { text="Nice Excel"; textSize=22f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0,-2,1f))
        top.addView(button("＋") { Toast.makeText(this,"More rows will be added in a later build",Toast.LENGTH_SHORT).show() })
        top.addView(button("Save CSV") { saveCsv() })
        root.addView(top)

        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(6,6,6,6); gravity=Gravity.CENTER_VERTICAL }
        actions.addView(action("Open CSV") { openCsv() })
        actions.addView(action("Formula help") { Toast.makeText(this,"Try =SUM(B1:B5), =A1+B1, or =B2*C2",Toast.LENGTH_LONG).show() })
        root.addView(actions)

        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(6,4,6,4); setBackgroundColor(Color.WHITE) }
        address=TextView(this).apply { text=selected; textSize=14f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,100,65)); setPadding(10,12,10,12); setBackgroundColor(Color.rgb(225,242,232)) }
        bar.addView(address)
        editor=EditText(this).apply { singleLine=true; textSize=14f; hint="Cell value or formula"; inputType=InputType.TYPE_CLASS_TEXT; setText(data[selected].orEmpty()); setPadding(8,4,8,4); setBackgroundColor(Color.WHITE); setOnEditorActionListener { _,_,_ -> applyCell(); true } }
        bar.addView(editor,LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(TextView(this).apply { text="✓"; textSize=24f; setTextColor(Color.rgb(20,125,70)); setPadding(12,4,12,4); setOnClickListener { applyCell() } })
        root.addView(bar)

        val horizontal=HorizontalScrollView(this)
        grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        horizontal.addView(grid)
        val vertical=ScrollView(this).apply { addView(horizontal) }
        root.addView(vertical,LinearLayout.LayoutParams(-1,0,1f))
        status=TextView(this).apply { text="Ready • Offline spreadsheet"; textSize=12f; setTextColor(Color.WHITE); setPadding(12,8,12,8); setBackgroundColor(Color.rgb(22,32,48)) }
        root.addView(status)
        setContentView(root)
        render()
    }

    private fun button(label:String, action:()->Unit)=TextView(this).apply {
        text=label; textSize=14f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(10,10,10,10); setOnClickListener { action() }
    }
    private fun action(label:String, run:()->Unit)=TextView(this).apply {
        text=label; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.rgb(25,80,55)); setPadding(12,9,12,9)
        setBackgroundColor(Color.rgb(222,240,230)); setOnClickListener { run() }
        val lp=LinearLayout.LayoutParams(-2,-2); lp.setMargins(3,0,3,0); layoutParams=lp
    }

    private fun applyCell() {
        data[selected]=editor.text.toString()
        render()
        status.text="Updated $selected"
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken,0)
    }
    private fun selectCell(a:String) { selected=a; address.text=a; editor.setText(data[a].orEmpty()); render() }

    private fun render() {
        grid.removeAllViews()
        val head=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        head.addView(cell("",48,true,null))
        for(c in 0 until cols) head.addView(cell(colName(c),112,true,null))
        grid.addView(head)
        for(r in 1..rows) {
            val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            line.addView(cell(r.toString(),48,true,null))
            for(c in 0 until cols) {
                val a="${colName(c)}$r"
                line.addView(cell(show(a),112,false,a))
            }
            grid.addView(line)
        }
    }

    private fun cell(value:String,width:Int,header:Boolean,key:String?):TextView {
        val v=TextView(this).apply {
            text=value; textSize=13f; gravity=if(header || key==null) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(if(header) 3 else 7,0,3,0)
            setTextColor(if(header) Color.rgb(55,65,75) else Color.rgb(25,33,42))
            setBackgroundColor(when { header -> Color.rgb(228,235,241); key==selected -> Color.rgb(214,241,224); else -> Color.WHITE })
            if(header) typeface=Typeface.DEFAULT_BOLD
            setOnClickListener { if(key!=null) selectCell(key) }
            setOnLongClickListener {
                if(key!=null) {
                    val input=EditText(this@MainActivity).apply { setText(data[key].orEmpty()) }
                    android.app.AlertDialog.Builder(this@MainActivity).setTitle("Edit $key").setView(input)
                        .setPositiveButton("Apply") { _,_ -> data[key]=input.text.toString(); selectCell(key) }
                        .setNegativeButton("Cancel",null).show()
                }; true
            }
        }
        val lp=LinearLayout.LayoutParams((width*resources.displayMetrics.density).toInt(),(40*resources.displayMetrics.density).toInt())
        lp.setMargins(0,0,1,1); v.layoutParams=lp
        v.setBackgroundResource(android.R.drawable.editbox_background)
        v.setBackgroundColor(when { header -> Color.rgb(228,235,241); key==selected -> Color.rgb(214,241,224); else -> Color.WHITE })
        return v
    }

    private fun show(a:String):String {
        val raw=data[a] ?: return ""
        if(!raw.startsWith("=")) return raw
        return try {
            val e=raw.drop(1)
            val range=Regex("(?i)^(SUM|AVERAGE|MIN|MAX|COUNT)\\(([A-Z]+\\d+):([A-Z]+\\d+)\\)$").matchEntire(e)
            val n=if(range!=null) {
                val vals=rangeValues(range.groupValues[2],range.groupValues[3]).mapNotNull { numeric(it) }
                when(range.groupValues[1].uppercase(Locale.US)) {
                    "SUM" -> vals.sum()
                    "AVERAGE" -> if(vals.isEmpty()) 0.0 else vals.average()
                    "MIN" -> vals.minOrNull() ?: 0.0
                    "MAX" -> vals.maxOrNull() ?: 0.0
                    else -> vals.size.toDouble()
                }
            } else {
                val expanded=Regex("(?i)\\b([A-Z]+\\d+)\\b").replace(e) { numeric(it.value)?.toString() ?: "0" }
                Arithmetic(expanded).parse()
            }
            if(n % 1.0==0.0) n.toLong().toString() else String.format(Locale.US,"%.4f",n)
        } catch(_:Exception) { "#ERROR" }
    }
    private fun numeric(a:String):Double? {
        val v=data[a] ?: return null
        return if(v.startsWith("=")) show(a).toDoubleOrNull() else v.toDoubleOrNull()
    }
    private fun rangeValues(start:String,end:String):List<String> {
        val a=split(start); val b=split(end); val out=mutableListOf<String>()
        for(r in minOf(a.second,b.second)..maxOf(a.second,b.second))
            for(c in minOf(a.first,b.first)..maxOf(a.first,b.first)) out.add("${colName(c-1)}$r")
        return out
    }
    private fun split(a:String):Pair<Int,Int> {
        val m=Regex("([A-Z]+)(\\d+)").matchEntire(a.uppercase(Locale.US))!!
        var c=0; m.groupValues[1].forEach { c=c*26+(it-'A'+1) }
        return c to m.groupValues[2].toInt()
    }
    private fun colName(index:Int):String {
        var n=index+1; var s=""
        while(n>0) { val r=(n-1)%26; s=('A'.code+r).toChar()+s; n=(n-1)/26 }
        return s
    }

    private fun saveCsv() {
        csvPending=(1..rows).joinToString("\n") { r ->
            (0 until cols).joinToString(",") { c ->
                val v=data["${colName(c)}$r"].orEmpty().replace("\"","\"\"")
                if(v.contains(",")||v.contains("\"")||v.contains("\n")) "\"$v\"" else v
            }
        }
        val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"; putExtra(Intent.EXTRA_TITLE,"NiceExcel.csv") }
        startActivityForResult(i,11)
    }
    private fun openCsv() {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="text/*" }
        startActivityForResult(i,12)
    }
    @Deprecated("Deprecated by Android")
    override fun onActivityResult(requestCode:Int,resultCode:Int,dataIntent:Intent?) {
        super.onActivityResult(requestCode,resultCode,dataIntent)
        if(resultCode!=RESULT_OK || dataIntent?.data==null) return
        try {
            if(requestCode==11) {
                contentResolver.openOutputStream(dataIntent.data!!)?.use { it.write(csvPending.toByteArray(Charsets.UTF_8)) }
                status.text="CSV saved"
            } else if(requestCode==12) {
                val text=contentResolver.openInputStream(dataIntent.data!!)?.bufferedReader()?.use { it.readText() }.orEmpty()
                data.clear()
                text.lines().take(rows).forEachIndexed { r,line ->
                    parseLine(line).take(cols).forEachIndexed { c,v -> if(v.isNotEmpty()) data["${colName(c)}${r+1}"]=v }
                }
                selectCell("A1"); status.text="CSV opened"
            }
        } catch(e:Exception) { status.text="File error: ${e.message}" }
    }
    private fun parseLine(line:String):List<String> {
        val out=mutableListOf<String>(); val cur=StringBuilder(); var quoted=false; var i=0
        while(i<line.length) {
            val ch=line[i]
            if(ch=='"') { if(quoted && i+1<line.length && line[i+1]=='"') { cur.append('"'); i++ } else quoted=!quoted }
            else if(ch==',' && !quoted) { out.add(cur.toString()); cur.setLength(0) } else cur.append(ch)
            i++
        }
        out.add(cur.toString()); return out
    }
}

private class Arithmetic(private val s:String) {
    private var p=0
    fun parse():Double { val x=expr(); skip(); if(p<s.length) error("Unexpected input"); return x }
    private fun expr():Double { var x=term(); while(true) { skip(); if(eat('+')) x+=term() else if(eat('-')) x-=term() else return x } }
    private fun term():Double { var x=factor(); while(true) { skip(); if(eat('*')) x*=factor() else if(eat('/')) x/=factor() else return x } }
    private fun factor():Double {
        skip()
        if(eat('+')) return factor()
        if(eat('-')) return -factor()
        if(eat('(')) { val x=expr(); if(!eat(')')) error("Missing )"); return x }
        val start=p; while(p<s.length && (s[p].isDigit()||s[p]=='.')) p++
        if(start==p) error("Number expected")
        return s.substring(start,p).toDouble()
    }
    private fun skip() { while(p<s.length && s[p].isWhitespace()) p++ }
    private fun eat(c:Char):Boolean { skip(); if(p<s.length&&s[p]==c) { p++; return true }; return false }
}
