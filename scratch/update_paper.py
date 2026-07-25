import sys
import os

paper_path = r'D:\research_2\scientific_paper_vi.md'

with open(paper_path, 'r', encoding='utf-8', errors='replace') as f:
    content = f.read()

# Let's inspect sections 5, 6, 7 in the paper.
print("Current total length (chars):", len(content))
print("Current word count:", len(content.split()))

# Let's check sections
sec5_tag = "## 5. Kết quả (Results)"
sec6_tag = "## 6. Thảo luận (Discussion)"
sec7_tag = "## 7. Kết luận (Conclusion)"
ack_tag  = "## Lời cảm ơn (Acknowledgements)"

i5 = content.find(sec5_tag)
i6 = content.find(sec6_tag)
i7 = content.find(sec7_tag)
i_ack = content.find(ack_tag)

print(f"Indices: Sec5={i5}, Sec6={i6}, Sec7={i7}, Ack={i_ack}")
