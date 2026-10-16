export type FeedbackKind = "success" | "error";

export const showFeedback = (
  message: string,
  kind: FeedbackKind = "success",
) => {
  window.dispatchEvent(
    new CustomEvent("flowmate:feedback", { detail: { message, kind } }),
  );
};
