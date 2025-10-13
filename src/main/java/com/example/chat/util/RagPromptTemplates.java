package com.example.chat.util;

import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * RAG (Retrieval Augmented Generation) 전용 프롬프트 템플릿 클래스
 * 다양한 RAG 패턴의 프롬프트 템플릿을 제공합니다.
 */
public class RagPromptTemplates {

    /**
     * 기본 RAG 프롬프트 템플릿
     * Spring AI의 ContextualQueryAugmenter 형식에 맞춤
     *
     * @return 기본 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createBasicRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Here are the relevant documents:

                {context} 

                **Instructions:**
                1. **CRITICAL**: Read and understand the EXACT question asked by the user
                2. **Focus**: Answer ONLY what the user is asking about
                3. **Document-Based**: Use only information from the provided documents
                4. **Relevance**: If documents don't contain information about the user's specific question, say "해당 정보를 찾을 수 없습니다"
                5. **Accuracy**: Provide accurate answers based on the documents
                6. **Language**: Answer in Korean
                """);
    }

    /**
     * Zero-shot RAG 프롬프트 템플릿
     * 명확하고 구체적인 지시를 제공하는 패턴
     *
     * @return Zero-shot RAG 프롬프트 템플릿
     */
    public static PromptTemplate createZeroShotRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Relevant Documents:

                {context}

                **Instructions:**
                1. **CRITICAL**: Read the user's question carefully and understand exactly what they are asking
                2. **Focus**: Answer ONLY what the user is asking about - do not answer a different question
                3. **Document-Based**: Use only information from the provided documents that is relevant to the user's specific question
                4. **Direct Answer**: Answer the question directly and clearly
                5. **Relevance**: If the documents don't contain information about the user's specific question, say "해당 정보를 찾을 수 없습니다"
                6. **Language**: Respond in Korean
                """);
    }

    /**
     * Few-shot Learning RAG 프롬프트 템플릿
     * 구체적이고 다양한 예시를 제공하는 패턴
     *
     * @return Few-shot Learning RAG 프롬프트 템플릿
     */
    public static PromptTemplate createFewShotRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Relevant Documents:

                {context}

                [Response Guidelines]
                When answering questions based on the provided documents:

                **Instructions:**
                1. **CRITICAL - Question Analysis**: FIRST, carefully read and understand the EXACT question asked by the user. Do NOT assume or interpret the question differently.
                2. **Question-Answer Match**: Ensure your answer directly addresses the specific question asked, not a different topic.
                3. **Document-Based Answer**: Use only information from the provided documents that is relevant to the user's specific question.
                4. **Relevance Check**: If the documents don't contain information about the user's specific question, say "제공된 문서에서 이에 대한 정보를 찾을 수 없습니다"
                5. **Accuracy**: Do not speculate on information not in the documents
                6. **Structure**: Organize your response with clear sections and bullet points
                7. **Language**: Respond in Korean
                8. **Markdown**: Use markdown formatting for better readability
                """);
    }

    /**
     * Chain-of-Thought RAG 프롬프트 템플릿
     * 단계별 사고 과정을 통해 논리적 추론하는 패턴
     *
     * @return Chain-of-Thought RAG 프롬프트 템플릿
     */
    public static PromptTemplate createChainOfThoughtRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Relevant Documents:

                {context}

                **Instructions:**
                When answering the question, please follow this thinking process:

                [Chain of Thought Process]
                1. **CRITICAL**: First, carefully read and understand the EXACT question asked by the user. Do NOT assume or interpret the question differently.
                2. Analyze the question and identify the key concepts that the user is actually asking about
                3. Examine the provided documents for information that is relevant to the user's specific question
                4. Break down the problem into logical steps based on what the user actually asked
                5. Think through each step carefully
                6. Consider different perspectives or approaches
                7. Synthesize the information into a coherent answer that directly addresses the user's question

                [Response Format]
                **Thinking Process:**
                1. [Step 1 in English]
                   - [Details]
                2. [Step 2 in English]
                   - [Details]

                **Final Answer:**
                [Well-structured answer in Korean using markdown]

                [Guidelines]
                1. Think step by step in English
                2. Keep thinking process concise and focused
                3. Provide final answer in Korean
                4. Focus on explaining "why" for key concepts
                5. Use only information from the provided documents
                6. Use markdown formatting (bold, bullet points)
                7. Include 3-5 key points with explanations
                8. If documents don't contain relevant information, say "해당 정보를 찾을 수 없습니다"
                """);
    }

    /**
     * 구조화된 RAG 프롬프트 템플릿
     * 문서를 체계적으로 분석하여 답변
     *
     * @return 구조화된 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createStructuredRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Relevant Documents:

                {context}

                **Answer Guidelines:**
                1. **Document Analysis**: Carefully examine each document for relevant information
                2. **Information Synthesis**: Combine information from multiple documents if needed
                3. **Accuracy**: Only use information explicitly stated in the documents
                4. **Completeness**: Provide comprehensive answers covering all relevant aspects
                5. **Clarity**: Organize your response in a clear, logical structure
                6. **Language**: Answer in Korean
                7. **Limitations**: If information is insufficient, clearly state what cannot be answered
                """);
    }

    /**
     * 전문가 스타일 RAG 프롬프트 템플릿
     * 전문적이고 상세한 답변 제공
     *
     * @return 전문가 스타일 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createExpertRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Reference Documents:

                {context}

                **Expert Response Guidelines:**
                1. **Comprehensive Analysis**: Thoroughly analyze all provided documents
                2. **Technical Depth**: Provide detailed technical explanations where applicable
                3. **Practical Examples**: Include practical examples or use cases when relevant
                4. **Best Practices**: Highlight best practices and recommendations
                5. **Contextual Understanding**: Consider the broader context and implications
                6. **Professional Tone**: Maintain a professional and authoritative tone
                7. **Korean Language**: Provide the answer in Korean
                8. **Accuracy**: Ensure all information is accurate and well-supported by the documents
                """);
    }

    /**
     * 간결한 RAG 프롬프트 템플릿
     * 핵심 정보만 간결하게 제공
     *
     * @return 간결한 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createConciseRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Documents:

                {context}

                **Response Guidelines:**
                1. **Conciseness**: Provide direct, to-the-point answers
                2. **Key Points**: Focus on the most important information
                3. **Clarity**: Use clear and simple language
                4. **Relevance**: Only include information directly relevant to the question
                5. **Korean**: Answer in Korean
                6. **Completeness**: Ensure the answer is complete despite being concise
                """);
    }

    /**
     * 교육적 RAG 프롬프트 템플릿
     * 학습과 이해를 돕는 답변 제공
     *
     * @return 교육적 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createEducationalRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Learning Materials:

                {context}

                **Educational Response Guidelines:**
                1. **Step-by-Step Explanation**: Break down complex concepts into understandable parts
                2. **Background Context**: Provide necessary background information
                3. **Examples**: Include relevant examples to illustrate concepts
                4. **Connections**: Show how different pieces of information relate to each other
                5. **Learning Focus**: Structure the response to facilitate learning and understanding
                6. **Korean Language**: Provide the explanation in Korean
                7. **Encouragement**: Encourage further exploration of the topic
                """);
    }

    /**
     * 역할 기반 RAG 프롬프트 템플릿
     * 특정 전문가 역할을 가정하고 답변
     *
     * @return 역할 기반 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createRoleBasedRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Reference Documents:

                {context}

                **Role-Based Response Guidelines:**
                You are an expert technical consultant with deep knowledge in the field. Please answer from this professional perspective:

                1. **Professional Analysis**: Provide insights from an expert perspective
                2. **Practical Application**: Focus on real-world applications and implications
                3. **Technical Depth**: Include technical details and considerations
                4. **Best Practices**: Highlight industry best practices and recommendations
                5. **Risk Assessment**: Mention potential challenges and solutions
                6. **Korean Language**: Provide the answer in Korean
                7. **Document-Based**: Use only information from the provided documents
                8. **Expert Tone**: Maintain authoritative and professional tone
                """);
    }

    /**
     * 단계별 분석 RAG 프롬프트 템플릿
     * 복잡한 문제를 단계별로 분석하여 답변
     *
     * @return 단계별 분석 RAG 프롬프트 템플릿
     */
    public static PromptTemplate createStepByStepRagPrompt() {
        return new PromptTemplate("""
                User Question: {query}

                Relevant Documents:

                {context}

                **Step-by-Step Analysis Guidelines:**
                1. **Problem Breakdown**: Divide the question into logical components
                2. **Document Analysis**: Examine each document for relevant information
                3. **Information Synthesis**: Combine findings from multiple documents
                4. **Solution Development**: Develop a comprehensive answer
                5. **Validation**: Ensure the answer addresses all aspects of the question

                **Response Format:**
                **Step 1: [Analysis step in English]**
                [Details and findings]

                **Step 2: [Analysis step in English]**
                [Details and findings]

                **Final Answer:**
                [Comprehensive answer in Korean with clear structure]

                **Instructions:**
                - Use only information from the provided documents
                - Think through each step methodically
                - Provide final answer in Korean
                - Use markdown formatting for clarity
                - If information is missing, clearly state what cannot be answered
                """);
    }
} 