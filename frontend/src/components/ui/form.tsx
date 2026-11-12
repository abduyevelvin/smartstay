"use client";

import * as React from "react";
import { Controller, ControllerProps, FieldPath, FieldValues, FormProvider, useFormContext, } from "react-hook-form";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

const Form = FormProvider;

type FormFieldContextValue<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
> = { name: TName };

const FormFieldContext = React.createContext<FormFieldContextValue>({} as FormFieldContextValue);

function FormField<
    TFieldValues extends FieldValues = FieldValues,
    TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,
>({...props}: ControllerProps<TFieldValues, TName>) {
    return (
        <FormFieldContext.Provider value={{name: props.name}}>
            <Controller {...props} />
        </FormFieldContext.Provider>
    );
}

const useFormField = () => {
    const fieldContext = React.useContext(FormFieldContext);
    const {getFieldState, formState} = useFormContext();
    const fieldState = getFieldState(fieldContext.name, formState);
    const id = React.useId();
    return {id, name: fieldContext.name, formItemId: `${id}-form-item`, ...fieldState};
};

const FormItemContext = React.createContext<{ id: string }>({} as { id: string });

function FormItem({className, ...props}: React.HTMLAttributes<HTMLDivElement>) {
    const id = React.useId();
    return (
        <FormItemContext.Provider value={{id}}>
            <div className={cn("space-y-1", className)} {...props} />
        </FormItemContext.Provider>
    );
}

function FormLabel({className, ...props}: React.ComponentProps<typeof Label>) {
    const {error, formItemId} = useFormField();
    return (
        <Label
            className={cn(error && "text-destructive", className)}
            htmlFor={formItemId}
            {...props}
        />
    );
}

function FormControl({...props}: React.ComponentProps<"div">) {
    const {error, formItemId} = useFormField();
    return (
        <div
            id={formItemId}
            aria-invalid={!!error}
            {...props}
        />
    );
}

function FormMessage({className, children, ...props}: React.HTMLAttributes<HTMLParagraphElement>) {
    const {error} = useFormField();
    const body = error ? String(error?.message) : children;
    if (!body) return null;
    return (
        <p className={cn("text-xs text-destructive", className)} {...props}>
            {body}
        </p>
    );
}

export { Form, FormField, FormItem, FormLabel, FormControl, FormMessage, useFormField };
