package uz.armebel

/** Model qismi: markazi (x,y,z), o'lchami (w,h,d) metrda. Model markazi pol sathida. */
class Part(val x: Float, val y: Float, val z: Float, val w: Float, val h: Float, val d: Float, val color: Int)

class Model(val name: String, val swatch: Int, val parts: List<Part>) {
    val radius: Float = parts.maxOf { maxOf(Math.abs(it.x) + it.w / 2, Math.abs(it.z) + it.d / 2) }
}

private fun b(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, c: Long) =
    Part(x, y, z, w, h, d, c.toInt())

private fun legs(lx: Float, lz: Float, h: Float, t: Float, c: Long) = listOf(
    b(lx, h / 2, lz, t, h, t, c), b(-lx, h / 2, lz, t, h, t, c),
    b(lx, h / 2, -lz, t, h, t, c), b(-lx, h / 2, -lz, t, h, t, c)
)

val CATALOG: List<Model> = listOf(
    Model("Divan", 0xFF5C7A99.toInt(), listOf(
        b(0f, 0.15f, 0f, 1.8f, 0.3f, 0.8f, 0xFF45607A),
        b(0f, 0.38f, 0.07f, 1.4f, 0.16f, 0.62f, 0xFF5C7A99),
        b(0f, 0.6f, -0.32f, 1.8f, 0.5f, 0.16f, 0xFF5C7A99),
        b(-0.8f, 0.45f, 0f, 0.2f, 0.3f, 0.8f, 0xFF45607A),
        b(0.8f, 0.45f, 0f, 0.2f, 0.3f, 0.8f, 0xFF45607A))),
    Model("Stol", 0xFF8D6E63.toInt(),
        listOf(b(0f, 0.72f, 0f, 1.2f, 0.05f, 0.7f, 0xFF8D6E63)) + legs(0.54f, 0.29f, 0.7f, 0.06f, 0xFF6D4C41)),
    Model("Stul", 0xFFC9A26B.toInt(),
        listOf(b(0f, 0.45f, 0f, 0.45f, 0.05f, 0.45f, 0xFFC9A26B),
               b(0f, 0.72f, -0.2f, 0.45f, 0.5f, 0.05f, 0xFFC9A26B)) + legs(0.2f, 0.2f, 0.43f, 0.05f, 0xFF8D6E63)),
    Model("Shkaf", 0xFFD7CCC8.toInt(), listOf(
        b(0f, 0.95f, 0f, 0.9f, 1.9f, 0.5f, 0xFFD7CCC8),
        b(-0.06f, 1.0f, 0.26f, 0.03f, 0.3f, 0.03f, 0xFF4E342E),
        b(0.06f, 1.0f, 0.26f, 0.03f, 0.3f, 0.03f, 0xFF4E342E))),
    Model("Karavot", 0xFFEFEBE9.toInt(), listOf(
        b(0f, 0.15f, 0f, 1.6f, 0.3f, 2.0f, 0xFF795548),
        b(0f, 0.4f, 0.02f, 1.5f, 0.2f, 1.9f, 0xFFF5F5F5),
        b(0f, 0.65f, -1.0f, 1.6f, 0.9f, 0.08f, 0xFF795548),
        b(-0.35f, 0.55f, -0.7f, 0.5f, 0.1f, 0.3f, 0xFF90CAF9),
        b(0.35f, 0.55f, -0.7f, 0.5f, 0.1f, 0.3f, 0xFF90CAF9))),
    Model("Tumba", 0xFFA1887F.toInt(), listOf(
        b(0f, 0.25f, 0f, 0.45f, 0.5f, 0.4f, 0xFFA1887F),
        b(0f, 0.34f, 0.205f, 0.14f, 0.03f, 0.02f, 0xFF3E2723),
        b(0f, 0.16f, 0.205f, 0.14f, 0.03f, 0.02f, 0xFF3E2723))),
    Model("Kitob javoni", 0xFF6D4C41.toInt(), listOf(
        b(-0.4f, 0.9f, 0f, 0.04f, 1.8f, 0.3f, 0xFF6D4C41),
        b(0.4f, 0.9f, 0f, 0.04f, 1.8f, 0.3f, 0xFF6D4C41),
        b(0f, 0.02f, 0f, 0.84f, 0.04f, 0.3f, 0xFF6D4C41),
        b(0f, 0.6f, 0f, 0.84f, 0.03f, 0.3f, 0xFF6D4C41),
        b(0f, 1.2f, 0f, 0.84f, 0.03f, 0.3f, 0xFF6D4C41),
        b(0f, 1.79f, 0f, 0.84f, 0.04f, 0.3f, 0xFF6D4C41),
        b(-0.25f, 0.8f, 0f, 0.1f, 0.35f, 0.22f, 0xFFE53935),
        b(-0.1f, 0.78f, 0f, 0.12f, 0.3f, 0.22f, 0xFF1E88E5),
        b(0.1f, 0.82f, 0f, 0.1f, 0.38f, 0.22f, 0xFF43A047),
        b(-0.2f, 1.4f, 0f, 0.12f, 0.36f, 0.22f, 0xFFFDD835),
        b(0.15f, 1.38f, 0f, 0.1f, 0.32f, 0.22f, 0xFF8E24AA))),
    Model("Lampa", 0xFFFFD54F.toInt(), listOf(
        b(0f, 0.015f, 0f, 0.3f, 0.03f, 0.3f, 0xFF424242),
        b(0f, 0.7f, 0f, 0.03f, 1.4f, 0.03f, 0xFF424242),
        b(0f, 1.52f, 0f, 0.35f, 0.25f, 0.35f, 0xFFFFE082))),
    Model("O'simlik", 0xFF43A047.toInt(), listOf(
        b(0f, 0.125f, 0f, 0.25f, 0.25f, 0.25f, 0xFF8D5524),
        b(0f, 0.45f, 0f, 0.38f, 0.4f, 0.38f, 0xFF388E3C),
        b(0f, 0.75f, 0f, 0.24f, 0.25f, 0.24f, 0xFF66BB6A))),
    Model("Televizor", 0xFF263238.toInt(), listOf(
        b(0f, 0.225f, 0f, 1.2f, 0.45f, 0.4f, 0xFF8D6E63),
        b(0f, 0.8f, 0f, 1.1f, 0.65f, 0.05f, 0xFF111111),
        b(0f, 0.8f, 0.03f, 1.04f, 0.58f, 0.01f, 0xFF1A237E),
        b(0f, 0.47f, 0f, 0.1f, 0.04f, 0.1f, 0xFF111111)))
)
