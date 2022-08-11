import json
from pathlib import Path
import os

RESULTS_FOLDER = "results"
CSV_DELIM = "|"
RESULTS_FILE = "csv_results.csv"

if os.path.exists(RESULTS_FILE):
	os.remove(RESULTS_FILE)

with open(RESULTS_FILE, "w") as csv:

	jsons = Path(RESULTS_FOLDER).glob('**/*.json')

	csv_str = "Class" + CSV_DELIM + "JNI Method" + CSV_DELIM + "File Function" + CSV_DELIM + "Params" + CSV_DELIM + "Returns" + CSV_DELIM + "Most Recent Function Call" + CSV_DELIM + "Flowed Values" + CSV_DELIM + "File Paths" + CSV_DELIM + "All Function String References"

	for json_file in jsons:
		if os.stat(json_file).st_size == 0:
			print(str(json_file.absolute()) + " is empty")
			continue

		with open(json_file) as json_data:
			data = json.load(json_data)

			ignored_json_keys = ["params", "returns", "strings", "class"]

			for jnifunc in data:
				for key, value in jnifunc.items():
					for file_func, values in value.items():

						if file_func in ignored_json_keys:
							continue

						params = ""
						returns = ""
						predecessors = ""
						results = ""
						strings = ""
						no_defs = ""
						unknown_tracking = ""

						if "params" in value:
							params = str(value["params"])

						if "returns" in value:
							returns = str(value["returns"])

						if "predecessors" in values:
							predecessors = str(values["predecessors"])

						if "no_defs" in values:
							results = "Likely from Java"

						if "unknown_tracking" in values:
							results = "Likely from Java"

						if "results" in values:
							results = values["results"]

						if "strings" in value:
							strings = str(value["strings"])

						files = []
						if type(results) == list:
							for string in results:
								if type(string) == str and string.startswith("/"):
									files.append(string)

						files = str(list(set(files)))
						results = str(results)

						# if "params" in values:
						csv_str += "\r\n" + value["class"] + CSV_DELIM + key + CSV_DELIM + file_func + CSV_DELIM + params + CSV_DELIM + \
                                                    returns + CSV_DELIM + predecessors + CSV_DELIM + results + CSV_DELIM + \
                                                    files + CSV_DELIM + strings

	csv.write(csv_str)
