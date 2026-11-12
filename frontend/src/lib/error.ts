import { AxiosError } from "axios";
import type { ErrorResponse } from "@/types";

/** Extract a human-readable message from any error (Axios or unknown). */
export function getErrorMessage(error: unknown): string {
    if (error instanceof AxiosError) {
        const data = error.response?.data as ErrorResponse | undefined;
        return data?.message ?? error.message ?? "Something went wrong";
    }
    if (error instanceof Error) return error.message;
    return "Something went wrong";
}
