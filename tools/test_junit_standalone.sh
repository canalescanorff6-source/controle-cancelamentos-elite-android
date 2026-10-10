#!/usr/bin/env bash
# Testes JVM sem SDK Android: executa os testes JUnit do núcleo Kotlin com API dublê de Room/Flow.
# NÃO substitui Gradle Android nem instrumentação em dispositivo.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/org/junit" "$TMP/classes"
cat > "$TMP/org/junit/Test.java" <<'JAVA'
package org.junit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {
  Class<? extends Throwable> expected() default None.class;
  public static class None extends Throwable {}
}
JAVA
cat > "$TMP/org/junit/Assert.java" <<'JAVA'
package org.junit;
import java.util.Arrays;
public class Assert {
    public static void assertTrue(boolean b) { if (!b) throw new AssertionError("expected true"); }
    public static void assertFalse(boolean b) { if (b) throw new AssertionError("expected false"); }
    public static void assertNull(Object b) { if (b != null) throw new AssertionError("expected null, found " + b); }
    public static void assertNotNull(Object b) { if (b == null) throw new AssertionError("expected non-null"); }
    public static void assertEquals(Object a,Object b) { if (!java.util.Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
    public static void assertEquals(long a,long b) { if (a != b) throw new AssertionError(a+" != "+b); }
    public static void assertArrayEquals(byte[] a, byte[] b) { if (!Arrays.equals(a,b)) throw new AssertionError("arrays differ"); }
}
JAVA
javac -d "$TMP/classes" "$TMP/org/junit/Assert.java" "$TMP/org/junit/Test.java"
cat > "$TMP/RoomStubs.kt" <<'KOTLIN'
package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(vararg val value: String, val unique: Boolean = false)
annotation class PrimaryKey
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 0)
annotation class Update
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = false)
object OnConflictStrategy { const val ABORT = 1; const val IGNORE = 2 }
open class RoomDatabase
KOTLIN
cat > "$TMP/FlowStubs.kt" <<'KOTLIN'
package kotlinx.coroutines.flow
interface Flow<out T>
KOTLIN
cat > "$TMP/Runner.kt" <<'KOTLIN'
import org.junit.Test
import java.lang.reflect.InvocationTargetException
fun main() {
    val tests = listOf(
        "br.com.controlecancelamentos.ocr.ReceiptParserTest",
        "br.com.controlecancelamentos.ocr.EncryptedArchiveTest",
        "br.com.controlecancelamentos.ocr.DomainValidationV3Test"
    )
    var passed = 0
    tests.forEach { name ->
        val clazz = Class.forName(name)
        val instance = clazz.getDeclaredConstructor().newInstance()
        clazz.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.forEach { method ->
            val expected = method.getAnnotation(Test::class.java).expected.java
            val error = try { method.invoke(instance); null } catch (e: InvocationTargetException) { e.targetException }
            val ok = if (expected == Test.None::class.java) error == null else error != null && expected.isInstance(error)
            if (!ok) error("FAIL: $name.${method.name}: ${error?.message ?: "expected ${expected.name}"}")
            passed++
        }
    }
    println("PASS: $passed testes do código Kotlin (JUnit stand-in, Room/Flow stubs, sem Android SDK)")
}
KOTLIN
kotlinc -classpath "$TMP/classes" "$TMP/RoomStubs.kt" "$TMP/FlowStubs.kt" "$TMP/Runner.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/data/Model.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/ocr/ReceiptParser.kt" \
    "$ROOT/app/src/main/java/br/com/controlecancelamentos/security/EncryptedArchive.kt" \
    "$ROOT/app/src/test/java/br/com/controlecancelamentos/ocr/ReceiptParserTest.kt" \
    "$ROOT/app/src/test/java/br/com/controlecancelamentos/ocr/EncryptedArchiveTest.kt" \
    "$ROOT/app/src/test/java/br/com/controlecancelamentos/ocr/DomainValidationV3Test.kt" \
    -include-runtime -d "$TMP/test.jar"
java -cp "$TMP/test.jar:$TMP/classes" RunnerKt
