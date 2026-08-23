import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

old_attach = """    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }"""
new_attach = """    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.util.getAttributionContext(newBase, "app_default"))
    }"""

content = content.replace(old_attach, new_attach)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
