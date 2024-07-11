package io.github.freewulkanowy.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.Sdk.ScrapperLoginType.ADFSLight
import io.github.freewulkanowy.sdk.Sdk.ScrapperLoginType.ADFSLightScoped
import io.github.freewulkanowy.sdk.Sdk.ScrapperLoginType.STANDARD
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = HiltTestApplication::class)
class Migration54Test : AbstractMigrationTest() {

    @Test
    fun `don't touch unrelated students`() = runTest {
        with(helper.createDatabase(dbName, 53)) {
            createStudent(1, STANDARD, "vulcan.net.pl", "rzeszow", "Jan Michniewicz")
            createStudent(2, ADFSLight, "umt.tarnow.pl", "tarnow", "Joanna Marcinkiewicz")
            close()
        }

        runMigrationsAndValidate(Migration54())
        val db = getMigratedRoomDatabase()
        val students = db.studentDao.loadAll()

        assertEquals(2, students.size)
        with(students[0]) {
            assertEquals(STANDARD.name, loginType)
            assertEquals("https://vulcan.net.pl", scrapperBaseUrl)
            assertEquals("rzeszow", symbol)
        }
        with(students[1]) {
            assertEquals(ADFSLight.name, loginType)
            assertEquals("https://umt.tarnow.pl", scrapperBaseUrl)
            assertEquals("tarnow", symbol)
        }
        db.close()
    }

    @Test
    fun `remove tomaszow mazowiecki students`() = runTest {
        with(helper.createDatabase(dbName, 53)) {
            createStudent(1, STANDARD, "vulcan.net.pl", "rzeszow", "Jan Michniewicz")
            createStudent(2, STANDARD, "vulcan.net.pl", "tomaszowmazowiecki", "Joanna Stec")
            createStudent(3, STANDARD, "vulcan.net.pl", "tomaszowmazowiecki", "Kacper Morawiecki")
            close()
        }

        runMigrationsAndValidate(Migration54())
        val db = getMigratedRoomDatabase()
        val students = db.studentDao.loadAll()
        assertEquals(1, students.size)
        with(students[0]) {
            assertEquals("rzeszow", symbol)
        }
        db.close()
    }

    @Test
    fun `migrate resman students`() = runTest {
        with(helper.createDatabase(dbName, 53)) {
            createStudent(1, ADFSLight, "resman.pl", "rzeszow", "Joanna Stec")
            createStudent(2, ADFSLight, "resman.pl", "rzeszow", "Kacper Morawiecki")
            createStudent(3, STANDARD, "vulcan.net.pl", "rzeszow", "Jan Michniewicz")
            close()
        }
        runMigrationsAndValidate(Migration54())
        val db = getMigratedRoomDatabase()
        val students = db.studentDao.loadAll()
        assertEquals(3, students.size)
        with(students[0]) {
            assertEquals(ADFSLightScoped.name, loginType)
            assertEquals("https://vulcan.net.pl", scrapperBaseUrl)
            assertEquals("rzeszowprojekt", symbol)
        }
        with(students[1]) {
            assertEquals(ADFSLightScoped.name, loginType)
            assertEquals("https://vulcan.net.pl", scrapperBaseUrl)
            assertEquals("rzeszowprojekt", symbol)
        }
        db.close()
    }

    private fun SupportSQLiteDatabase.createStudent(
        id: Long,
        loginType: Sdk.ScrapperLoginType,
        host: String,
        symbol: String,
        studentName: String,
    ) {
        insert("Students", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("scrapper_base_url", "https://$host")
            put("mobile_base_url", "")
            put("login_type", loginType.name)
            put("login_mode", "SCRAPPER")
            put("certificate_key", "")
            put("private_key", "")
            put("is_parent", false)
            put("email", "jan@fakelog.cf")
            put("password", "******")
            put("symbol", symbol)
            put("student_id", Random.nextInt())
            put("user_login_id", id)
            put("user_name", studentName)
            put("student_name", studentName)
            put("school_id", "123")
            put("school_short", "")
            put("school_name", "")
            put("class_name", "")
            put("class_id", Random.nextInt())
            put("is_current", false)
            put("registration_date", "0")
            put("id", id)
            put("nick", "")
            put("avatar_color", "")
        })
    }
}
