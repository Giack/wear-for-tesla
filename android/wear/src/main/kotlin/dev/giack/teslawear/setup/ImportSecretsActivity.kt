package dev.giack.teslawear.setup

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import dev.giack.teslawear.R
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.graph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Started by `setup.sh push-watch` after it streamed files/import.json into the app's private
 * directory. Imports the secrets, deletes the file and finishes.
 */
class ImportSecretsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = filesDir.resolve("import.json")
        val message = try {
            graph.secretsStore.import(Json.parseToJsonElement(file.readText()).jsonObject)
            graph.resetProvisioning()
            getString(R.string.import_done)
        } catch (e: Exception) {
            Diag.w("secret import failed: ${e.javaClass.simpleName}")
            getString(R.string.import_failed)
        } finally {
            file.delete()
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
