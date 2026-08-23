import re

with open("app/src/main/java/com/example/ui/NetraMissionControl.kt", "r") as f:
    content = f.read()

graphs_code = """
        item {
            SectionTitle("Live Mission Graphs", "View Trends")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Collecting Live Data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionTitle("Mission History", "View All")
"""

content = content.replace('item {\n            SectionTitle("Mission History", "View All")', graphs_code.strip())

with open("app/src/main/java/com/example/ui/NetraMissionControl.kt", "w") as f:
    f.write(content)
