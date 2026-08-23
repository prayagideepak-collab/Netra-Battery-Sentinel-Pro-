#!/bin/bash
sed -i 's/it.endLevel/it.endPercentage ?: state.percentage/g' app/src/main/java/com/example/ui/NetraIntelligenceCenter.kt
sed -i 's/it.maxTemp/it.maxTemperature/g' app/src/main/java/com/example/ui/NetraIntelligenceCenter.kt
sed -i 's/app.mahConsumed/app.consumedMah/g' app/src/main/java/com/example/ui/NetraIntelligenceCenter.kt
sed -i 's/Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(top = 4.dp))/Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween)/g' app/src/main/java/com/example/ui/NetraIntelligenceCenter.kt
