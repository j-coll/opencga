import subprocess
import sys
import urllib.request


def run_az_command(cmdArray):
    try:
        print("Attempt run {}".format(cmdArray))
        subprocess.check_call(cmdArray)
        print("Install completed successfully")
    except subprocess.CalledProcessError as e:
        print("Failed running: {} error: {}".format(cmdArray, e))
        exit(4)

if len(sys.argv) != 11:
    print(
        "Expected 'poolid', 'vm_size', 'mount_args', 'artifact_location', 'artifact_sas', 'subnet_id', 'max_node_count', 'msi_name', 'resource_group_name' , 'batch_account_name'"
    )
    exit(1)

pool_id = str(sys.argv[1])
vm_size = str(sys.argv[2])
mount_args = str(sys.argv[3])
artifact_location = str(sys.argv[4])
artifact_sas = str(sys.argv[5])
subnet_id = str(sys.argv[6])
max_node_count = str(sys.argv[7])
msi_name = str(sys.argv[8])
resource_group_name = str(sys.argv[9])
batch_account_name = str(sys.argv[10])

url = "{0}/azurebatch/pool.json{1}".format(artifact_location, artifact_sas)
response = urllib.request.urlopen(url)
data = response.read()
text = data.decode("utf-8") 

# Replace the target string
text = text.replace("POOL_ID_HERE", pool_id)
text = text.replace("VM_SIZE_HERE", vm_size)
text = text.replace("MOUNT_ARGS_HERE", mount_args)
text = text.replace("ARTIFACT_LOCATION_HERE", artifact_location)
text = text.replace("ARTIFACT_SAS_HERE", artifact_sas)
text = text.replace("SUBNET_ID_HERE", subnet_id)
text = text.replace("MAX_NODE_COUNT_HERE", max_node_count)


# Write the file out again
with open("pool.complete.json", "w") as file:
    file.write(text)

run_az_command(["az", "login", "--identity", "-u", msi_name])
run_az_command(["az", "batch", "account", "login", "--name", batch_account_name, "-g", resource_group_name])
run_az_command(["az", "batch", "pool", "create", "--json-file", "pool.complete.json"])
