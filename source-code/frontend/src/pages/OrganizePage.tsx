import { useState } from "react";
import { FolderPlus, Pencil, Plus, Save, Tags, Trash2, X } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import type { Category, Tag } from "../types";

export default function OrganizePage() {
  const queryClient = useQueryClient();
  const [categoryName, setCategoryName] = useState("");
  const [categoryColor, setCategoryColor] = useState("#4F6DF5");
  const [categoryEditId, setCategoryEditId] = useState<string>();
  const [tagName, setTagName] = useState("");
  const [tagColor, setTagColor] = useState("#20A875");
  const [tagEditId, setTagEditId] = useState<string>();
  const categoryQuery = useQuery({
    queryKey: ["categories"],
    queryFn: async () => (await api.get<Category[]>("/categories")).data,
  });
  const tagQuery = useQuery({
    queryKey: ["tags"],
    queryFn: async () => (await api.get<Tag[]>("/tags")).data,
  });
  const categories = categoryQuery.data || [];
  const tags = tagQuery.data || [];
  const createCategory = useMutation({
    mutationFn: () =>
      api.post("/categories", {
        name: categoryName.trim(),
        color: categoryColor,
        icon: "folder",
      }),
    onSuccess: () => {
      setCategoryName("");
      queryClient.invalidateQueries({ queryKey: ["categories"] });
    },
  });
  const createTag = useMutation({
    mutationFn: () =>
      api.post("/tags", { name: tagName.trim(), color: tagColor }),
    onSuccess: () => {
      setTagName("");
      queryClient.invalidateQueries({ queryKey: ["tags"] });
    },
  });
  const updateCategory = useMutation({
    mutationFn: () =>
      api.put(`/categories/${categoryEditId}`, {
        name: categoryName.trim(),
        color: categoryColor,
        icon: "folder",
      }),
    onSuccess: () => {
      setCategoryEditId(undefined);
      setCategoryName("");
      queryClient.invalidateQueries({ queryKey: ["categories"] });
    },
  });
  const updateTag = useMutation({
    mutationFn: () =>
      api.put(`/tags/${tagEditId}`, {
        name: tagName.trim(),
        color: tagColor,
      }),
    onSuccess: () => {
      setTagEditId(undefined);
      setTagName("");
      queryClient.invalidateQueries({ queryKey: ["tags"] });
    },
  });
  const removeCategory = useMutation({
    mutationFn: (id: string) => api.delete(`/categories/${id}`),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["categories"] }),
  });
  const removeTag = useMutation({
    mutationFn: (id: string) => api.delete(`/tags/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["tags"] }),
  });
  const pageError =
    categoryQuery.error ||
    tagQuery.error ||
    createCategory.error ||
    createTag.error ||
    updateCategory.error ||
    updateTag.error ||
    removeCategory.error ||
    removeTag.error;
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">TỔ CHỨC CÔNG VIỆC</span>
          <h1>Danh mục và nhãn</h1>
          <p>Tạo hệ thống phân loại phù hợp với cách bạn làm việc.</p>
        </div>
      </div>
      {pageError && (
        <div className="form-error organize-error" role="alert">
          {getApiErrorMessage(pageError, "Không thể xử lý danh mục hoặc nhãn.")}
        </div>
      )}
      <div className="organize-grid">
        <section className="panel organize-card">
          <div className="panel-heading">
            <div>
              <FolderPlus />
              <h2>Danh mục</h2>
            </div>
            <b>{categories.length}</b>
          </div>
          <form
            className={`inline-create ${categoryEditId ? "editing" : ""}`}
            onSubmit={(event) => {
              event.preventDefault();
              if (!categoryName.trim()) return;
              if (categoryEditId) updateCategory.mutate();
              else createCategory.mutate();
            }}
          >
            <input
              type="color"
              value={categoryColor}
              onChange={(event) =>
                setCategoryColor(event.target.value.toUpperCase())
              }
            />
            <input
              value={categoryName}
              onChange={(event) => setCategoryName(event.target.value)}
              placeholder="Ví dụ: Học tập"
              maxLength={80}
            />
            <button
              className="primary small"
              disabled={
                !categoryName.trim() ||
                createCategory.isPending ||
                updateCategory.isPending
              }
            >
              {categoryEditId ? <Save size={16} /> : <Plus size={16} />}
              {categoryEditId ? "Lưu" : "Thêm"}
            </button>
            {categoryEditId && (
              <button
                type="button"
                className="icon-btn inline-cancel"
                aria-label="Hủy sửa danh mục"
                onClick={() => {
                  setCategoryEditId(undefined);
                  setCategoryName("");
                }}
              >
                <X size={15} />
              </button>
            )}
          </form>
          <div className="organize-list">
            {categories.map((category) => (
              <div key={category.id}>
                <i style={{ background: category.color }} />
                <span>{category.name}</span>
                <button
                  className="icon-btn"
                  onClick={() => {
                    setCategoryEditId(category.id);
                    setCategoryName(category.name);
                    setCategoryColor(category.color);
                  }}
                  aria-label={`Sửa ${category.name}`}
                >
                  <Pencil size={15} />
                </button>
                <button
                  className="icon-btn"
                  onClick={() => {
                    if (window.confirm(`Xóa danh mục “${category.name}”?`)) {
                      removeCategory.mutate(category.id);
                    }
                  }}
                  aria-label={`Xóa ${category.name}`}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {!categories.length && <p>Chưa có danh mục.</p>}
          </div>
        </section>
        <section className="panel organize-card">
          <div className="panel-heading">
            <div>
              <Tags />
              <h2>Nhãn</h2>
            </div>
            <b>{tags.length}</b>
          </div>
          <form
            className={`inline-create ${tagEditId ? "editing" : ""}`}
            onSubmit={(event) => {
              event.preventDefault();
              if (!tagName.trim()) return;
              if (tagEditId) updateTag.mutate();
              else createTag.mutate();
            }}
          >
            <input
              type="color"
              value={tagColor}
              onChange={(event) =>
                setTagColor(event.target.value.toUpperCase())
              }
            />
            <input
              value={tagName}
              onChange={(event) => setTagName(event.target.value)}
              placeholder="Ví dụ: Quan trọng"
              maxLength={60}
            />
            <button
              className="primary small"
              disabled={
                !tagName.trim() || createTag.isPending || updateTag.isPending
              }
            >
              {tagEditId ? <Save size={16} /> : <Plus size={16} />}
              {tagEditId ? "Lưu" : "Thêm"}
            </button>
            {tagEditId && (
              <button
                type="button"
                className="icon-btn inline-cancel"
                aria-label="Hủy sửa nhãn"
                onClick={() => {
                  setTagEditId(undefined);
                  setTagName("");
                }}
              >
                <X size={15} />
              </button>
            )}
          </form>
          <div className="organize-list">
            {tags.map((tag) => (
              <div key={tag.id}>
                <i style={{ background: tag.color }} />
                <span>{tag.name}</span>
                <button
                  className="icon-btn"
                  onClick={() => {
                    setTagEditId(tag.id);
                    setTagName(tag.name);
                    setTagColor(tag.color);
                  }}
                  aria-label={`Sửa ${tag.name}`}
                >
                  <Pencil size={15} />
                </button>
                <button
                  className="icon-btn"
                  onClick={() => {
                    if (window.confirm(`Xóa nhãn “${tag.name}”?`)) {
                      removeTag.mutate(tag.id);
                    }
                  }}
                  aria-label={`Xóa ${tag.name}`}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {!tags.length && <p>Chưa có nhãn.</p>}
          </div>
        </section>
      </div>
    </>
  );
}
