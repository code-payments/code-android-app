#
# For all .proto files, strip the validation parameters & replace inline
#

root=$(pwd)

# 1. hack: first add a couple newlines after all "];"
find "${root}"/service/protos/src/main/proto -name "*.proto" -type f -exec sh -c "awk '{gsub(/];/, \"];\n\n\"); print}' {} > tmp && mv tmp {}" \;

# 2. strip everything between square brackets [...] ignoring lines starting with //
find "${root}"/service/protos/src/main/proto -name "*.proto" -type f -exec sh -c "awk '!/^[[:space:]]*\/\// {gsub(/ \[.*\]/, \"\");} {print}' {} > tmp && mv tmp {}" \;

find "${root}"/service/protos/src/main/proto -name "*.proto" -type f | while read -r file; do
    awk '
    BEGIN { in_repeated = 0; buffer = "" }
    {
        if ($0 ~ /^[[:space:]]*(repeated|bytes|[A-Za-z0-9_]+).*=.*\[/) {
            in_repeated = 1
            buffer = $0
        } else if (in_repeated) {
            buffer = buffer " " $0
        }

        if (in_repeated && $0 ~ /;/) {
            gsub(/\[.*\]/, "", buffer)
            print buffer
            in_repeated = 0
            buffer = ""
        } else if (!in_repeated && $0 !~ /^[[:space:]]*option[[:space:]]*\(validate\.required\)[[:space:]]*=[[:space:]]*true;/) {
            print $0
        }
    }
    ' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
done

# 3. add a newline after all trailing } brackets
#find "${root}"/service/protos/src/main/proto -name "*.proto" -type f -exec sh -c "awk '{gsub(/}/, \"}\n\"); print}' {} > tmp && mv tmp {}" \;

# 4. strip validate import statement
find "${root}"/service/protos/src/main/proto -name "*.proto" -type f -exec sh -c "awk -v RS='' '{gsub(/import \"validate\/validate.proto\";/, \"\"); print}' {} > tmp && mv tmp {}" \;

# 5. add a newline after all trailing } brackets
#find "${root}"/service/protos/src/main/proto -name "*.proto" -type f -exec sh -c "awk '{gsub(/}/, \"}\n\"); print}' {} > tmp && mv tmp {}" \;