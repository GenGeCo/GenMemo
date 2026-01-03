package com.example.genmemo.util

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.ItemType
import com.example.genmemo.data.model.MemoryItem
import com.example.genmemo.ui.theme.CategoryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportResult(
    val success: Boolean,
    val categoriesImported: Int = 0,
    val itemsImported: Int = 0,
    val errorMessage: String? = null
)

data class ExportData(
    val categoria: String,
    val colore: String,
    val schede: List<ExportItem>
)

data class ExportItem(
    val tipo: String,
    val domanda: String,
    val risposta: String,
    val prompt: String? = null
)

object ImportExportManager {

    private val colorNameMap = mapOf(
        "rosso" to CategoryColors[0],
        "blu" to CategoryColors[1],
        "verde" to CategoryColors[2],
        "viola" to CategoryColors[3],
        "arancione" to CategoryColors[4],
        "rosa" to CategoryColors[5],
        "ciano" to CategoryColors[6],
        "giallo" to CategoryColors[7]
    )

    private val colorToNameMap = colorNameMap.entries.associate { (k, v) -> v.toArgb().toLong() to k }

    /**
     * Genera le istruzioni per l'AI - SOLO TESTO
     */
    fun generateAIInstructionsTextOnly(): String {
        return """
=== ISTRUZIONI PER GENERARE FLASHCARD GENMEMO ===

Genera un file JSON con questo formato ESATTO.
Salva il file come: nome_corso.json

FORMATO:
{
  "categoria": "Nome della Categoria",
  "colore": "viola",
  "schede": [
    {
      "tipo": "QUESTION",
      "domanda": "testo della domanda",
      "risposta": "testo della risposta"
    }
  ]
}

COLORI DISPONIBILI: rosso, blu, verde, viola, arancione, rosa, ciano, giallo

REGOLE IMPORTANTI:
1. Il campo "tipo" deve essere "QUESTION" per domande testuali
2. Per le immagini usa "tipo": "IMAGE" (ma servira' un file ZIP separato)
3. Ogni scheda DEVE avere: tipo, domanda, risposta
4. Le risposte devono essere BREVI (1-3 parole idealmente)
5. Evita virgolette doppie nel testo, usa virgolette singole

=== ESEMPIO COMPLETO - Corso Inglese Base ===

{
  "categoria": "Inglese Base",
  "colore": "blu",
  "schede": [
    {"tipo": "QUESTION", "domanda": "Come si dice 'cane' in inglese?", "risposta": "Dog"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'gatto' in inglese?", "risposta": "Cat"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'casa' in inglese?", "risposta": "House"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'buongiorno'?", "risposta": "Good morning"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'grazie'?", "risposta": "Thank you"},
    {"tipo": "QUESTION", "domanda": "Traduci: 'I am happy'", "risposta": "Sono felice"},
    {"tipo": "QUESTION", "domanda": "Qual e' il plurale di 'child'?", "risposta": "Children"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'mangiare'?", "risposta": "To eat"},
    {"tipo": "QUESTION", "domanda": "Traduci: 'Where are you from?'", "risposta": "Di dove sei?"},
    {"tipo": "QUESTION", "domanda": "Come si dice 'acqua'?", "risposta": "Water"}
  ]
}

=== ESEMPIO - Capitali del Mondo ===

{
  "categoria": "Capitali del Mondo",
  "colore": "verde",
  "schede": [
    {"tipo": "QUESTION", "domanda": "Capitale dell'Italia?", "risposta": "Roma"},
    {"tipo": "QUESTION", "domanda": "Capitale della Francia?", "risposta": "Parigi"},
    {"tipo": "QUESTION", "domanda": "Capitale della Germania?", "risposta": "Berlino"},
    {"tipo": "QUESTION", "domanda": "Capitale della Spagna?", "risposta": "Madrid"},
    {"tipo": "QUESTION", "domanda": "Capitale del Giappone?", "risposta": "Tokyo"}
  ]
}

=== ORA DIMMI COSA VUOI E GENERO IL FILE ===

Esempi di richieste:
- "Fammi 50 flashcard di inglese livello B1"
- "Crea un corso sulle capitali europee"
- "Genera flashcard sui verbi irregolari inglesi"
- "Fammi un corso di storia romana"
- "Crea flashcard di anatomia umana"

        """.trimIndent()
    }

    /**
     * Genera le istruzioni per l'AI - TESTO + IMMAGINI DA URL
     */
    fun generateAIInstructionsWithImages(): String {
        return """
=== ISTRUZIONI PER GENERARE FLASHCARD GENMEMO CON IMMAGINI ===

Genera un file JSON con questo formato ESATTO.
Salva il file come: nome_corso.json

FORMATO CON IMMAGINI:
{
  "categoria": "Nome della Categoria",
  "colore": "viola",
  "schede": [
    {
      "tipo": "QUESTION",
      "domanda": "testo della domanda",
      "risposta": "testo della risposta"
    },
    {
      "tipo": "IMAGE",
      "immagine_url": "https://url-immagine-libera.jpg",
      "risposta": "cosa rappresenta l'immagine",
      "prompt": "Domanda da mostrare (es: Chi e'? Cosa e'?)",
      "fonte": "Wikipedia/Wikimedia Commons"
    }
  ]
}

COLORI DISPONIBILI: rosso, blu, verde, viola, arancione, rosa, ciano, giallo

REGOLE IMPORTANTI:
1. "tipo": "QUESTION" per domande testuali
2. "tipo": "IMAGE" per schede con immagini
3. Per le immagini USA SOLO URL di immagini LIBERE da:
   - Wikimedia Commons (https://commons.wikimedia.org)
   - Wikipedia (https://upload.wikimedia.org)
   - Unsplash (https://images.unsplash.com)
   - Pexels (https://images.pexels.com)
4. Il campo "fonte" indica da dove viene l'immagine (per attribuzione)
5. Il campo "prompt" e' la domanda mostrata sotto l'immagine
6. Le risposte devono essere BREVI (1-3 parole)

=== ESEMPIO - Personaggi Storici ===

{
  "categoria": "Personaggi Storici",
  "colore": "giallo",
  "schede": [
    {
      "tipo": "IMAGE",
      "immagine_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg/800px-Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg",
      "risposta": "Mona Lisa",
      "prompt": "Che quadro famoso e' questo?",
      "fonte": "Wikimedia Commons"
    },
    {
      "tipo": "IMAGE",
      "immagine_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/1200px-Cat03.jpg",
      "risposta": "Gatto",
      "prompt": "Che animale e'?",
      "fonte": "Wikimedia Commons"
    },
    {
      "tipo": "QUESTION",
      "domanda": "Chi ha dipinto la Mona Lisa?",
      "risposta": "Leonardo da Vinci"
    }
  ]
}

=== ESEMPIO - Bandiere del Mondo ===

{
  "categoria": "Bandiere del Mondo",
  "colore": "blu",
  "schede": [
    {
      "tipo": "IMAGE",
      "immagine_url": "https://upload.wikimedia.org/wikipedia/en/thumb/0/03/Flag_of_Italy.svg/1200px-Flag_of_Italy.svg.png",
      "risposta": "Italia",
      "prompt": "Di quale paese e' questa bandiera?",
      "fonte": "Wikipedia"
    },
    {
      "tipo": "IMAGE",
      "immagine_url": "https://upload.wikimedia.org/wikipedia/en/thumb/c/c3/Flag_of_France.svg/1200px-Flag_of_France.svg.png",
      "risposta": "Francia",
      "prompt": "Di quale paese e' questa bandiera?",
      "fonte": "Wikipedia"
    }
  ]
}

=== COME TROVARE IMMAGINI LIBERE ===

1. Vai su https://commons.wikimedia.org
2. Cerca l'argomento (es: "Mona Lisa", "Cat", "Italy flag")
3. Clicca sull'immagine
4. Copia l'URL diretto dell'immagine (tasto destro -> Copia indirizzo immagine)
5. Usa URL che finiscono con .jpg, .png o .svg

=== ORA DIMMI COSA VUOI E GENERO IL FILE ===

Esempi di richieste:
- "Fammi flashcard sulle bandiere europee con immagini"
- "Crea un corso sui monumenti famosi con foto"
- "Genera flashcard sugli animali con immagini da Wikipedia"
- "Fammi un corso di arte con quadri famosi"
- "Crea flashcard sui presidenti americani con foto"

        """.trimIndent()
    }

    /**
     * Parsa un file JSON e restituisce categoria e items
     * Supporta sia formato testo che formato con URL immagini
     */
    fun parseJsonImport(jsonString: String): Pair<Category?, List<MemoryItem>>? {
        return try {
            val json = JSONObject(jsonString)

            val categoryName = json.getString("categoria")
            val colorName = json.optString("colore", "viola").lowercase()
            val colorValue = colorNameMap[colorName]?.toArgb()?.toLong()
                ?: CategoryColors[3].toArgb().toLong() // default viola

            val category = Category(
                name = categoryName,
                color = colorValue
            )

            val schedeArray = json.getJSONArray("schede")
            val items = mutableListOf<MemoryItem>()

            for (i in 0 until schedeArray.length()) {
                val scheda = schedeArray.getJSONObject(i)
                val tipo = scheda.getString("tipo").uppercase()

                val itemType = when (tipo) {
                    "IMAGE" -> ItemType.IMAGE
                    else -> ItemType.QUESTION
                }

                // Per le immagini, usa immagine_url se presente, altrimenti domanda
                val question = if (itemType == ItemType.IMAGE) {
                    scheda.optString("immagine_url", scheda.optString("domanda", ""))
                } else {
                    scheda.getString("domanda")
                }

                val risposta = scheda.getString("risposta")
                val prompt = scheda.optString("prompt", "Chi/Cosa è?")

                items.add(
                    MemoryItem(
                        type = itemType,
                        question = question, // Per IMAGE sarà l'URL o il path
                        answer = risposta,
                        categoryId = null, // Will be set after category is inserted
                        prompt = prompt,
                        score = 0,
                        interval = 1f,
                        nextReviewDate = System.currentTimeMillis()
                    )
                )
            }

            Pair(category, items)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Scarica un'immagine da URL e la salva localmente
     */
    suspend fun downloadImage(imageUrl: String, destDir: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "GenMemo-App/1.0")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val fileName = "img_${System.currentTimeMillis()}_${imageUrl.hashCode().toString(16)}.jpg"
                    val destFile = File(destDir, fileName)

                    connection.inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    destFile.absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Verifica se una stringa è un URL
     */
    fun isUrl(str: String): Boolean {
        return str.startsWith("http://") || str.startsWith("https://")
    }

    /**
     * Esporta una categoria e i suoi items in formato JSON
     */
    fun exportToJson(category: Category, items: List<MemoryItem>): String {
        val json = JSONObject()
        json.put("categoria", category.name)
        json.put("colore", colorToNameMap[category.color] ?: "viola")

        val schedeArray = JSONArray()
        items.forEach { item ->
            val scheda = JSONObject()
            scheda.put("tipo", if (item.type == ItemType.IMAGE) "IMAGE" else "QUESTION")
            scheda.put("domanda", item.question)
            scheda.put("risposta", item.answer)
            if (item.type == ItemType.IMAGE) {
                scheda.put("prompt", item.prompt)
            }
            schedeArray.put(scheda)
        }
        json.put("schede", schedeArray)

        return json.toString(2) // Pretty print with 2-space indent
    }

    /**
     * Esporta tutte le categorie e items in un singolo JSON
     */
    fun exportAllToJson(categories: List<Category>, itemsByCategory: Map<Long, List<MemoryItem>>): String {
        val jsonArray = JSONArray()

        categories.forEach { category ->
            val items = itemsByCategory[category.id] ?: emptyList()
            val categoryJson = JSONObject()
            categoryJson.put("categoria", category.name)
            categoryJson.put("colore", colorToNameMap[category.color] ?: "viola")

            val schedeArray = JSONArray()
            items.forEach { item ->
                val scheda = JSONObject()
                scheda.put("tipo", if (item.type == ItemType.IMAGE) "IMAGE" else "QUESTION")
                scheda.put("domanda", item.question)
                scheda.put("risposta", item.answer)
                if (item.type == ItemType.IMAGE) {
                    scheda.put("prompt", item.prompt)
                }
                schedeArray.put(scheda)
            }
            categoryJson.put("schede", schedeArray)
            jsonArray.put(categoryJson)
        }

        return jsonArray.toString(2)
    }

    /**
     * Legge un file JSON da Uri
     */
    fun readJsonFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Scrive JSON su file
     */
    fun writeJsonToUri(context: Context, uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonContent)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Crea un file ZIP con JSON e immagini per export
     */
    fun exportToZip(
        context: Context,
        uri: Uri,
        category: Category,
        items: List<MemoryItem>,
        imageFiles: Map<String, File>
    ): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // Add JSON file
                    val jsonContent = exportToJson(category, items)
                    val jsonEntry = ZipEntry("schede.json")
                    zipOut.putNextEntry(jsonEntry)
                    zipOut.write(jsonContent.toByteArray())
                    zipOut.closeEntry()

                    // Add image files
                    imageFiles.forEach { (fileName, file) ->
                        if (file.exists()) {
                            val imageEntry = ZipEntry(fileName)
                            zipOut.putNextEntry(imageEntry)
                            file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Importa da file ZIP (JSON + immagini)
     */
    fun importFromZip(
        context: Context,
        uri: Uri,
        imageDestDir: File
    ): Triple<Category?, List<MemoryItem>, Map<String, String>>? {
        return try {
            var jsonContent: String? = null
            val extractedImages = mutableMapOf<String, String>() // original name -> new path

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry

                    while (entry != null) {
                        val fileName = entry.name

                        if (fileName.endsWith(".json")) {
                            // Read JSON content
                            jsonContent = zipIn.bufferedReader().readText()
                        } else if (isImageFile(fileName)) {
                            // Extract image
                            val destFile = File(imageDestDir, "import_${System.currentTimeMillis()}_$fileName")
                            FileOutputStream(destFile).use { fos ->
                                zipIn.copyTo(fos)
                            }
                            extractedImages[fileName] = destFile.absolutePath
                        }

                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            if (jsonContent != null) {
                val (category, items) = parseJsonImport(jsonContent!!) ?: return null

                // Update image paths in items
                val updatedItems = items.map { item ->
                    if (item.type == ItemType.IMAGE && extractedImages.containsKey(item.question)) {
                        item.copy(question = extractedImages[item.question]!!)
                    } else {
                        item
                    }
                }

                Triple(category, updatedItems, extractedImages)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isImageFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".webp")
    }
}
