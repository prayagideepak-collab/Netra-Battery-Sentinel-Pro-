import re

with open("app/src/main/java/com/example/BatteryApplication.kt", "r") as f:
    content = f.read()

old_attach = """    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }"""
new_attach = """    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.example.util.getAttributionContext(base, "system"))
    }"""

content = content.replace(old_attach, new_attach)

with open("app/src/main/java/com/example/BatteryApplication.kt", "w") as f:
    f.write(content)
