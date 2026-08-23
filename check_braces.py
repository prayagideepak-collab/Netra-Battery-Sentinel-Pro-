with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    lines = f.readlines()

start = 3271 # fun SettingsScreen is 3272 (0-indexed 3271)
depth = 0
for i in range(start, 4540):
    line = lines[i]
    depth += line.count('{')
    depth -= line.count('}')
    if depth == 0 and line.strip() == "}":
        print(f"Balanced at line {i+1}")
        break

print(f"End depth: {depth}")
