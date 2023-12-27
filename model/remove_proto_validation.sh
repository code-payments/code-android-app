#
# For all .proto files, strip the validation parameters & replace inline
#

# 1. hack: first add a couple newlines after all "];" 
find proto/ -name "*.proto" -type f -exec sh -c "awk '{gsub(/];/, \"];\n\n\"); print}' {} > tmp && mv tmp {}" \;

# 2. strip everything between square brackets [...]
find proto/ -name "*.proto" -type f -exec sh -c "awk -v RS='' '{gsub(/ \[.*\]/, \"\"); print}' {} > tmp && mv tmp {}" \;

# 3. add a newline after all trailing } brackets
find proto/ -name "*.proto" -type f -exec sh -c "awk '{gsub(/}/, \"}\n\"); print}' {} > tmp && mv tmp {}" \;

# 4. strip validate import statement
find proto/ -name "*.proto" -type f -exec sh -c "awk -v RS='' '{gsub(/import \"validate\/validate.proto\";/, \"\"); print}' {} > tmp && mv tmp {}" \;

# 5. strip validate required options
find proto/ -name "*.proto" -type f -exec sh -c "awk -v RS='' '{gsub(/option \(validate.required\) = true;/, \"\"); print}' {} > tmp && mv tmp {}" \;

# 6. add a newline after all trailing } brackets
find proto/ -name "*.proto" -type f -exec sh -c "awk '{gsub(/}/, \"}\n\"); print}' {} > tmp && mv tmp {}" \;
